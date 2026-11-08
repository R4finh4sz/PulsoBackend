package pulsoescolar_api.service.metrics;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.metrics.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.exception.common.BusinessValidationException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class SchoolMetricsService {
    private final CurrentUser currentUser;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public SchoolMetricsResponse get() {
        var school = requireSchool();
        Long id = school.getId();
        return new SchoolMetricsResponse(id, school.getNome(), new SchoolMetricsResponse.BigNumbers(
                countUsers(id, Role.STUDENT), countUsers(id, Role.TEACHER),
                count("select count(c) from Classroom c where c.school.id = :schoolId and c.deletedAt is null", id),
                count("select count(s) from Subject s where s.classroom.school.id = :schoolId and s.classroom.deletedAt is null", id),
                count("select count(a) from Activity a where a.subject.classroom.school.id = :schoolId and a.subject.classroom.deletedAt is null", id),
                count("select count(s) from QuizSubmission s where s.activity.subject.classroom.school.id = :schoolId and s.activity.subject.classroom.deletedAt is null and s.student.deletedAt is null", id)));
    }

    @Transactional(readOnly = true)
    public SchoolGrowthResponse growth(int year) {
        var school = requireSchool();
        if (year < 1 || year > 9999) throw new BusinessValidationException("Informe um ano de 1 a 9999.");
        var zone = ZoneId.of("America/Sao_Paulo");
        var start = LocalDate.of(year, 1, 1);
        var rows = entityManager.createNativeQuery("""
                select extract(month from u.created_at at time zone 'America/Sao_Paulo'), u.role, count(*)
                from school_users u join user_schools s on s.user_id = u.id
                where s.school_id = :schoolId and u.deleted_at is null
                  and u.role in ('STUDENT', 'TEACHER')
                  and u.created_at >= :start and u.created_at < :end
                group by extract(month from u.created_at at time zone 'America/Sao_Paulo'), u.role
                """, Object[].class)
                .setParameter("schoolId", school.getId())
                .setParameter("start", start.atStartOfDay(zone).toInstant())
                .setParameter("end", start.plusYears(1).atStartOfDay(zone).toInstant())
                .getResultList();
        long[] students = new long[12], teachers = new long[12];
        for (Object result : rows) {
            var row = (Object[]) result;
            int month = ((Number) row[0]).intValue() - 1;
            long count = ((Number) row[2]).longValue();
            if ("STUDENT".equals(row[1])) students[month] = count;
            else teachers[month] = count;
        }
        var months = new ArrayList<SchoolGrowthResponse.Month>();
        long cumulativeStudents = 0, cumulativeTeachers = 0;
        for (int month = 0; month < 12; month++) {
            cumulativeStudents += students[month];
            cumulativeTeachers += teachers[month];
            months.add(new SchoolGrowthResponse.Month(month + 1, students[month], teachers[month],
                    cumulativeStudents, cumulativeTeachers));
        }
        return new SchoolGrowthResponse(school.getId(), school.getNome(), year, months,
                BigDecimal.valueOf(cumulativeStudents).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(cumulativeTeachers).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                countUndatedUsers(school.getId(), Role.STUDENT), countUndatedUsers(school.getId(), Role.TEACHER));
    }

    private School requireSchool() {
        var user = currentUser.get();
        var school = user.getSchool();
        if (user.getRole() != Role.PEDAGOGICAL_COORDINATOR || school == null
                || !user.belongsToSchool(school.getId())) {
            throw new AccessDeniedException("Coordenador sem escola ativa autorizada.");
        }
        return school;
    }

    private long countUndatedUsers(Long schoolId, Role role) {
        return entityManager.createQuery("select count(u) from SchoolUser u join u.schools s "
                + "where s.id = :schoolId and u.role = :role and u.deletedAt is null and u.createdAt is null", Long.class)
                .setParameter("schoolId", schoolId).setParameter("role", role).getSingleResult();
    }

    private long countUsers(Long schoolId, Role role) {
        return entityManager.createQuery("select count(distinct u) from SchoolUser u join u.schools s "
                + "where s.id = :schoolId and u.role = :role and u.deletedAt is null", Long.class)
                .setParameter("schoolId", schoolId).setParameter("role", role).getSingleResult();
    }

    private long count(String query, Long schoolId) {
        return entityManager.createQuery(query, Long.class).setParameter("schoolId", schoolId).getSingleResult();
    }
}

package pulsoescolar_api.service.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.activity.ActivityStatus;
import pulsoescolar_api.dto.activity.TeacherReportResponse;
import pulsoescolar_api.dto.activity.TeacherReportResponse.*;
import pulsoescolar_api.entity.activity.Activity;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.activity.ActivityRepository;
import pulsoescolar_api.repository.activity.QuizSubmissionRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherReportService {
    private final CurrentUser currentUser;
    private final ActivityRepository activities;
    private final QuizSubmissionRepository submissions;
    private final Clock clock;

    public TeacherReportResponse report(Long schoolId, Long classroomId, ActivityStatus status) {
        var teacher = currentUser.get();
        if (teacher.getRole() != Role.TEACHER) throw new AccessDeniedException("Esta consulta exige um professor.");
        var now = clock.instant();
        var selected = activities.findForTeacher(teacher.getId()).stream().filter(activity -> {
            var room = activity.getSubject().getClassroom();
            var school = room.getSchool();
            boolean finished = activity.getClosesAt() != null && !now.isBefore(activity.getClosesAt());
            return (schoolId == null || school != null && schoolId.equals(school.getId()))
                    && (classroomId == null || classroomId.equals(room.getId()))
                    && (status == null || status == (finished ? ActivityStatus.FINISHED : ActivityStatus.OPEN));
        }).toList();
        var recipients = new HashMap<Long, Set<Long>>();
        var delivered = new HashMap<Long, Set<Long>>();
        var grades = new HashMap<Long, List<BigDecimal>>();
        var ids = selected.stream().map(Activity::getId).toList();
        if (!ids.isEmpty()) {
            for (var row : activities.findRecipients(ids)) {
                recipients.computeIfAbsent(row.getActivityId(), key -> new HashSet<>()).add(row.getStudentId());
            }
            for (var row : submissions.findForReport(ids)) {
                // Uma entrega comprova recebimento inclusive no legado sem lista de destinatários.
                recipients.computeIfAbsent(row.getActivityId(), key -> new HashSet<>()).add(row.getStudentId());
                delivered.computeIfAbsent(row.getActivityId(), key -> new HashSet<>()).add(row.getStudentId());
                if (row.getGradedAt() != null && row.getScore() != null) {
                    grades.computeIfAbsent(row.getActivityId(), key -> new ArrayList<>()).add(row.getScore());
                }
            }
        }
        var total = new Totals();
        var byRoom = new TreeMap<Long, Totals>();
        var rooms = new HashMap<Long, pulsoescolar_api.entity.classroom.Classroom>();
        for (var activity : selected) {
            var room = activity.getSubject().getClassroom();
            rooms.put(room.getId(), room);
            var roomTotals = byRoom.computeIfAbsent(room.getId(), key -> new Totals());
            var assigned = recipients.getOrDefault(activity.getId(), Set.of());
            var submitted = delivered.getOrDefault(activity.getId(), Set.of());
            var overdue = new HashSet<Long>();
            if (activity.getClosesAt() != null && !now.isBefore(activity.getClosesAt())) {
                overdue.addAll(assigned);
                overdue.removeAll(submitted);
            }
            for (var target : List.of(total, roomTotals)) {
                target.complete &= activity.isRecipientHistoryComplete();
                target.activityCount++;
                target.expected += assigned.size();
                target.submitted += submitted.size();
                target.students.addAll(assigned);
                target.overdue.addAll(overdue);
                for (var grade : grades.getOrDefault(activity.getId(), List.of())) {
                    target.score = target.score.add(grade);
                    target.graded++;
                }
            }
        }
        var rows = byRoom.entrySet().stream().map(entry -> {
            var room = rooms.get(entry.getKey());
            var school = room.getSchool();
            var stats = entry.getValue();
            return new ClassroomReport(room.getId(), room.getName(), school == null ? null : school.getId(),
                    school == null ? null : school.getNome(), stats.complete ? (long) stats.students.size() : null,
                    stats.activityCount, stats.average(), stats.expected(), stats.submitted, stats.percentage(),
                    stats.overdue(), stats.complete);
        }).toList();
        return new TeacherReportResponse(now, new Summary(total.average(), total.expected(), total.submitted,
                total.percentage(), total.overdue(), rows.size(), total.activityCount, total.complete), rows);
    }

    private static class Totals {
        boolean complete = true;
        long activityCount, expected, submitted, graded;
        BigDecimal score = BigDecimal.ZERO;
        Set<Long> students = new HashSet<>(), overdue = new HashSet<>();

        Long expected() { return complete ? expected : null; }
        Long overdue() { return complete ? (long) overdue.size() : null; }
        BigDecimal average() {
            return graded == 0 ? null : score.divide(BigDecimal.valueOf(graded).multiply(BigDecimal.TEN), 2, RoundingMode.HALF_UP);
        }
        BigDecimal percentage() {
            return !complete || expected == 0 ? null : BigDecimal.valueOf(submitted).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(expected), 2, RoundingMode.HALF_UP);
        }
    }
}

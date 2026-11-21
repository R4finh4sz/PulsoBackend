package pulsoescolar_api.service.metrics;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.metrics.AdminUsageMetricsResponse;
import pulsoescolar_api.dto.metrics.AdminUsageMetricsResponse.StateDemand;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.exception.common.BusinessValidationException;

@Service
@RequiredArgsConstructor
public class AdminUsageMetricsService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AdminUsageMetricsResponse get(String month) {
        YearMonth period = parseMonth(month);
        Instant start = period == null ? null : period.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant end = period == null ? null : period.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        var users = entityManager.createQuery("""
                select u.role, count(u) from SchoolUser u
                where %s
                group by u.role
                """.formatted(period == null ? "u.deletedAt is null" : "u.createdAt >= :start and u.createdAt < :end"),
                Object[].class);
        var demand = entityManager.createQuery("""
                select upper(trim(s.uf)), count(r) from RegistrationRequest r join r.school s
                %s
                group by upper(trim(s.uf))
                order by count(r) desc, upper(trim(s.uf)) asc nulls last
                """.formatted(period == null ? "" : "where r.requestedAt >= :start and r.requestedAt < :end"),
                Object[].class);
        if (period != null) {
            users.setParameter("start", start).setParameter("end", end);
            demand.setParameter("start", start).setParameter("end", end);
        }
        var byRole = new EnumMap<Role, Long>(Role.class);
        for (Role role : Role.values()) byRole.put(role, 0L);
        for (var row : users.getResultList()) byRole.put((Role) row[0], (Long) row[1]);
        long total = byRole.values().stream().mapToLong(Long::longValue).sum();
        var states = demand.getResultList().stream()
                .map(row -> new StateDemand((String) row[0], (Long) row[1]))
                .toList();
        return new AdminUsageMetricsResponse(month, total, byRole, states);
    }

    private YearMonth parseMonth(String month) {
        if (month == null) return null;
        try {
            if (month.matches("[0-9]{4}-[0-9]{2}") && !month.startsWith("0000")) {
                return YearMonth.parse(month);
            }
        } catch (DateTimeParseException ignored) {
            // Return the same validation error for all invalid months.
        }
        throw new BusinessValidationException("Informe o mês no formato AAAA-MM.");
    }
}

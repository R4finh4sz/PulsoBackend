package pulsoescolar_api.service.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogRetentionJob {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        var cutoff = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusYears(1);
        jdbc.update("DELETE FROM audit_events WHERE occurred_at < ?", cutoff);
        jdbc.update("DELETE FROM authentication_events WHERE occurred_at < ?", cutoff);
    }
}

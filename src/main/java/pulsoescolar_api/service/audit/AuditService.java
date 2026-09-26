package pulsoescolar_api.service.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type) VALUES (?, ?)",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), event.code());
    }
}

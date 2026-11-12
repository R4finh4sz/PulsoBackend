package pulsoescolar_api.service.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthAuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthAuditEvent event, Long userId) { insert(event, userId); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuthAuditEvent event, Long userId) { insert(event, userId); }

    private void insert(AuthAuditEvent event, Long userId) {
        jdbc.update("INSERT INTO authentication_events (occurred_at, event_type, actor_user_id) VALUES (?, ?, ?)",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), event.name(), userId);
    }
}

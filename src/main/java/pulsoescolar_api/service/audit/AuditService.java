package pulsoescolar_api.service.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CurrentUser currentUser;
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        Long actorUserId = currentUser.get().getId();
        jdbc.update("INSERT INTO audit_events (occurred_at, event_type, actor_user_id) VALUES (?, ?, ?)",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), event.code(), actorUserId);
    }
}

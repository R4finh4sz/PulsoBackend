package pulsoescolar_api.entity.auth;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import pulsoescolar_api.service.audit.AuthAuditEvent;

@Entity @Table(name = "authentication_events") @Immutable
public class AuthAuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AuthAuditEvent eventType;
    private Long actorUserId;
}

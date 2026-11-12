package pulsoescolar_api.entity.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AuthSession {
    @Column(nullable = false) private boolean twoFactorVerified;
    private String codeHash;
    private Instant codeExpiresAt;
    private Instant resendAvailableAt;
    @Column(nullable = false) private int codeAttempts;
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private SchoolUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

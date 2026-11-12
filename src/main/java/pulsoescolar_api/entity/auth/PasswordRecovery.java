package pulsoescolar_api.entity.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "password_recoveries")
@Getter @Setter @NoArgsConstructor
public class PasswordRecovery {
    @Id private Long userId;
    private String codeHash;
    @Column(nullable = false) private Instant codeExpiresAt;
    @Column(nullable = false) private Instant resendAvailableAt;
    @Column(nullable = false) private int codeAttempts;
    private String resetTokenHash;
    private Instant resetExpiresAt;
}

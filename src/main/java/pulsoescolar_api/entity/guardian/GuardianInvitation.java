package pulsoescolar_api.entity.guardian;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "guardian_invitations")
@Getter @Setter @NoArgsConstructor
public class GuardianInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "confirmation_id") private GuardianConfirmation confirmation;
    @Column(nullable = false, unique = true, length = 128) private String tokenHash;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant decidedAt;
    @Enumerated(EnumType.STRING) @Column(length = 30) private GuardianDecision decision;
    @Enumerated(EnumType.STRING) @Column(length = 30) private GuardianRelationship relationship;
    private Long termsVersion;
    private Integer privacyVersion;
    private Instant supersededAt;
    private java.util.UUID renewalSessionId;

    public boolean isOpen(Instant now) {
        return decidedAt == null && supersededAt == null && expiresAt.isAfter(now);
    }
}

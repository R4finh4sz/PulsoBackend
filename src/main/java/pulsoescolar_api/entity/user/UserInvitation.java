package pulsoescolar_api.entity.user;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pulsoescolar_api.entity.school.School;

@Entity
@Table(name = "user_invitations")
@Getter @Setter @NoArgsConstructor
public class UserInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 128) private String tokenHash;
    @Column(nullable = false, length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id") private School school;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invited_by_id") private SchoolUser invitedBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    private Instant verifiedAt;
    private String verificationCodeHash;
    private Instant verificationCodeExpiresAt;
    private Instant resendAvailableAt;
    @Column(nullable = false) private int verificationAttempts;
}
package pulsoescolar_api.entity.user;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.notification.UserNotification;

@Entity
@Table(name = "school_invitations", uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "recipient_id"}))
@Getter @Setter @NoArgsConstructor
public class SchoolInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id") private School school;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id") private SchoolUser recipient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invited_by_id") private SchoolUser invitedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SchoolInvitationStatus status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant respondedAt;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "notification_id", unique = true)
    private UserNotification notification;
}

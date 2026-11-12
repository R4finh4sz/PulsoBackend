package pulsoescolar_api.entity.subject;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.entity.notification.UserNotification;

@Entity @Table(name = "subject_invitations", uniqueConstraints = @UniqueConstraint(columnNames = {"subject_id", "recipient_id"}))
@Getter @Setter @NoArgsConstructor
public class SubjectInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subject_id") private Subject subject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id") private SchoolUser recipient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inviter_id") private SchoolUser inviter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SubjectInvitationStatus status;
    @Column(nullable = false) private Instant createdAt;
    private Instant respondedAt;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "notification_id", unique = true)
    private UserNotification notification;
}

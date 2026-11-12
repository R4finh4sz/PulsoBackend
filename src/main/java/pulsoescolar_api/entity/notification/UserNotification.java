package pulsoescolar_api.entity.notification;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity @Table(name = "user_notifications")
@Getter @Setter @NoArgsConstructor
public class UserNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id") private SchoolUser recipient;
    @Column(nullable = false, length = 50) private String type;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000) private String message;
    @Column(nullable = false) private Instant createdAt;
    private Instant readAt;
}

package pulsoescolar_api.entity.user;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "account_deletion_requests")
@Getter @Setter @NoArgsConstructor
public class AccountDeletionRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requester_id", nullable = false)
    private SchoolUser requester;
    @Column(nullable = false, length = 2000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeletionStatus status;
    @Column(nullable = false) private Instant requestedAt;
    private Instant reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_id") private SchoolUser reviewedBy;
    @Column(length = 2000) private String reviewReason;
}

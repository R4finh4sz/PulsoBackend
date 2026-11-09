package pulsoescolar_api.entity.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import pulsoescolar_api.entity.school.School;

@Entity @Table(name = "student_transfers")
@Getter @Setter @NoArgsConstructor
public class StudentTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private SchoolUser student;
    @ManyToOne(optional = false) private School origin;
    @ManyToOne(optional = false) private School destination;
    @ManyToOne(optional = false) private SchoolUser requestedBy;
    @Column(nullable = false) private Instant requestedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RegistrationStatus status;
    @ManyToOne private SchoolUser reviewedBy;
    private Instant reviewedAt;
}

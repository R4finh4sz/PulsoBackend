package pulsoescolar_api.entity.classroom;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity @Table(name = "classroom_deliberations")
@Getter @Setter @NoArgsConstructor
public class ClassroomDeliberation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Classroom classroom;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private School school;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeliberationAction action;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "performed_by_id") private SchoolUser performedBy;
    @Column(nullable = false) private Instant performedAt;
    @ElementCollection
    @CollectionTable(name = "classroom_deliberation_students", joinColumns = @JoinColumn(name = "deliberation_id"))
    private List<DeliberationEntry> entries = new ArrayList<>();
}

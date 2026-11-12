package pulsoescolar_api.entity.classroom;

import jakarta.persistence.*;
import lombok.*;
import pulsoescolar_api.entity.user.SchoolUser;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DeliberationEntry {
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "student_id") private SchoolUser student;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeliberationResult result;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_classroom_id") private Classroom destination;
}

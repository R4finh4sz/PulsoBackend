package pulsoescolar_api.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name="classrooms")
@Getter @Setter @NoArgsConstructor
public class Classroom {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, unique=true, length=100) private String name;
 @ManyToMany
 @JoinTable(name="classroom_teachers", joinColumns=@JoinColumn(name="classroom_id"), inverseJoinColumns=@JoinColumn(name="teacher_id"))
 private Set<SchoolUser> teachers = new HashSet<>();
}

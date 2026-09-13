package pulsoescolar_api.entity.classroom;

import pulsoescolar_api.entity.user.SchoolUser;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name="classrooms", uniqueConstraints=@UniqueConstraint(name="uk_classroom_name_identifier", columnNames={"name", "identifier"}))
@Getter @Setter @NoArgsConstructor
public class Classroom {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=100) private String name;
 @Column(nullable=false, length=1) private String identifier;
 @ManyToMany
 @JoinTable(name="classroom_teachers", joinColumns=@JoinColumn(name="classroom_id"), inverseJoinColumns=@JoinColumn(name="teacher_id"))
 private Set<SchoolUser> teachers = new HashSet<>();
}

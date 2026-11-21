package pulsoescolar_api.entity.classroom;

import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.entity.school.School;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name="classrooms")
@Getter @Setter @NoArgsConstructor
public class Classroom {
 private java.time.Instant deletedAt;
 private java.time.Instant graduatedAt;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") private School school;
 @Column(nullable=false, length=100) private String name;
 @Column(nullable=false, length=1) private String identifier;
 @ManyToMany
 @JoinTable(name="classroom_teachers", joinColumns=@JoinColumn(name="classroom_id"), inverseJoinColumns=@JoinColumn(name="teacher_id"))
 private Set<SchoolUser> teachers = new HashSet<>();
 /** Série (1 a 3) lida do nome "Nº ano · Turno"; null se o nome não seguir o padrão. */
 public Integer resolveGradeLevel() {
  if (name == null || name.isEmpty() || !Character.isDigit(name.charAt(0))) return null;
  return Character.getNumericValue(name.charAt(0));
 }
}

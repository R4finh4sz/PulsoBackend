package pulsoescolar_api.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="subjects", uniqueConstraints=@UniqueConstraint(columnNames={"classroom_id","name"}))
@Getter @Setter @NoArgsConstructor
public class Subject {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=100) private String name;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="classroom_id") private Classroom classroom;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="teacher_id") private SchoolUser teacher;
}

package pulsoescolar_api.entity.subject;

import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.user.SchoolUser;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="subjects", uniqueConstraints=@UniqueConstraint(columnNames={"classroom_id","name"}))
@Getter @Setter @NoArgsConstructor
public class Subject {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=100) private String name;
 @Column(nullable=false, length=5000) private String syllabus = "";
 @ElementCollection
 @CollectionTable(name="subject_topics", joinColumns=@JoinColumn(name="subject_id"))
 @OrderColumn(name="topic_order")
 private java.util.List<SubjectTopic> topics = new java.util.ArrayList<>();
 @ElementCollection
 @CollectionTable(name="subject_themes", joinColumns=@JoinColumn(name="subject_id"),
         uniqueConstraints=@UniqueConstraint(columnNames={"subject_id", "name"}))
 @Column(name="name", nullable=false, length=40)
 private java.util.Set<String> themes = new java.util.HashSet<>();
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="classroom_id") private Classroom classroom;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="teacher_id") private SchoolUser teacher;
 @ManyToMany
 @JoinTable(name="subject_teachers", joinColumns=@JoinColumn(name="subject_id"),
         inverseJoinColumns=@JoinColumn(name="teacher_id"))
 private java.util.Set<SchoolUser> collaborators = new java.util.HashSet<>();

 public boolean hasTeacher(Long userId) {
  return teacher.getId().equals(userId) || collaborators.stream().anyMatch(t -> t.getId().equals(userId));
 }
}

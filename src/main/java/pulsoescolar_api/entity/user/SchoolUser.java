package pulsoescolar_api.entity.user;

import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="school_users")
@Getter @Setter @NoArgsConstructor
public class SchoolUser {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") private School school;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=200) private String fullName;
 @Column(nullable=false, unique=true, length=50) private String ra;
 @Column(nullable=false, unique=true) private String email;
 @Column(nullable=false) private String passwordHash;
 @Column(nullable=false) private boolean firstLogin;
 @Column(nullable=false) private boolean termsAccepted;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private Role role;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="classroom_id") private Classroom classroom;
}

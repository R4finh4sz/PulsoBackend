package pulsoescolar_api.entity.user;

import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="school_users")
@Getter @Setter @NoArgsConstructor
public class SchoolUser {
 @Column private java.time.Instant deletedAt;
 @Version private long version;
 private java.time.Instant twoFactorResendAvailableAt;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") private School school;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=200) private String fullName;
 @Column(nullable=false, unique=true, length=50) private String ra;
 @Column(nullable=false, unique=true) private String email;
 @Column(nullable=false) private String passwordHash;
 @Column(nullable=false) private boolean termsAccepted;
 @ElementCollection
 @CollectionTable(name="user_terms_acceptances", joinColumns=@JoinColumn(name="user_id"))
 @Column(name="version", nullable=false)
 private java.util.Set<Long> acceptedTermVersions = new java.util.HashSet<>();
 public java.util.List<String> getTermsAcceptedVersions() {
  return acceptedTermVersions.stream().sorted().map(pulsoescolar_api.entity.terms.TermsVersion::label).toList();
 }
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private Role role;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="classroom_id") private Classroom classroom;
}

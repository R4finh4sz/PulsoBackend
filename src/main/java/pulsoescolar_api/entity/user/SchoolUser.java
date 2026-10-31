package pulsoescolar_api.entity.user;

import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="school_users")
@Getter @Setter @NoArgsConstructor
public class SchoolUser {
 private java.time.LocalDate birthDate;
 @Column(columnDefinition="bytea") private byte[] profilePhoto;
 @Column private java.time.Instant deletedAt;
 @Version private long version;
 private java.time.Instant twoFactorResendAvailableAt;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") private School school;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="active_school_id") private School activeSchool;
 @ManyToMany
 @JoinTable(name="user_schools", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="school_id"))
 private java.util.Set<School> schools = new java.util.HashSet<>();
 public School getSchool() { return activeSchool == null ? school : activeSchool; }
 public School getEnrollmentSchool() { return school; }
 public void setSchool(School school) {
  this.school = school;
  this.activeSchool = school;
  if (school != null && schools.stream().noneMatch(value -> value == school
          || school.getId() != null && school.getId().equals(value.getId()))) schools.add(school);
 }
 public boolean belongsToSchool(Long schoolId) {
  return schools.stream().anyMatch(value -> value.getId().equals(schoolId));
 }
 public boolean isInActiveSchool(Classroom room) {
  return java.util.Objects.equals(getSchool() == null ? null : getSchool().getId(),
          room.getSchool() == null ? null : room.getSchool().getId());
 }
 public Classroom getActiveClassroom() {
  return classroom == null || classroom.getDeletedAt() != null || !isInActiveSchool(classroom) ? null : classroom;
 }
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

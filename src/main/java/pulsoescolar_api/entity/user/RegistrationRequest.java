package pulsoescolar_api.entity.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;
import pulsoescolar_api.entity.school.School;

@Entity
@Table(name = "registration_requests")
@Getter @Setter @NoArgsConstructor
public class RegistrationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(nullable = false, length = 100) private String fullName;
    private LocalDate birthDate;
    @Column(nullable = false, unique = true, length = 50) private String ra;
    @Column(nullable = false, unique = true) private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id") private School school;
    @Column(nullable = false) private Long termsVersion;
    @Column(columnDefinition = "bytea") private byte[] profilePhoto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RegistrationStatus status;
    @Column(nullable = false) private Instant requestedAt;
    private Instant reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_id") private SchoolUser reviewedBy;
    @Column(length = 2000) private String reviewReason;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private SchoolUser user;
}

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
    private Integer studentTermVersion;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(nullable = false, length = 100) private String fullName;
    private LocalDate birthDate;
    @Column(nullable = false, unique = true, length = 50) private String ra;
    @Column(nullable = false, unique = true) private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id") private School school;
    private Long termsVersion;
    /** Privacy policy version accepted at registration; null when none was published. */
    private Integer privacyVersion;
    @Column(columnDefinition = "bytea") private byte[] profilePhoto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RegistrationStatus status;
    @Column(nullable = false) private Instant requestedAt;
    private Instant reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_id") private SchoolUser reviewedBy;
    @Column(length = 2000) private String reviewReason;
    private Instant identityCheckedAt;
    private Instant birthDateCorrectedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "birth_date_corrected_by_id") private SchoolUser birthDateCorrectedBy;
    @Column(length = 2000) private String birthDateCorrectionReason;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private SchoolUser user;
}

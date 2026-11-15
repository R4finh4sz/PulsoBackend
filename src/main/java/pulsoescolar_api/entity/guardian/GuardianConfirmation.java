package pulsoescolar_api.entity.guardian;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import pulsoescolar_api.entity.user.RegistrationRequest;
import pulsoescolar_api.entity.user.SchoolUser;

/** Guardian confirmation of a minor student registration. Name and e-mail stay student-informed until the school checks them. */
@Entity
@Table(name = "guardian_confirmations")
@Getter @Setter @NoArgsConstructor
public class GuardianConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registration_id") private RegistrationRequest registration;
    /** Null only while the student has not informed the guardian yet (birth date corrected by the school). */
    @Column(length = 100) private String guardianName;
    @Column(length = 254) private String guardianEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private GuardianStatus status;
    @Column(length = 2000) private String correctionReason;
    @Column(nullable = false) private int correctionCount;
    @Column(length = 128) private String correctionTokenHash;
    private Instant correctionTokenExpiresAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "checked_by_id") private SchoolUser checkedBy;
    private Instant checkedAt;
    @Column(length = 200) private String checkMethod;
    @Column(length = 2000) private String checkJustification;
    @Column(nullable = false) private int resendCount;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}

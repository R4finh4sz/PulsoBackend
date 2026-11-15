package pulsoescolar_api.entity.agreement;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.SchoolUser;

/** Evidence that a school accepted a given version of the data processing agreement. */
@Entity
@Table(name = "school_data_agreements")
@Immutable
@Getter
@NoArgsConstructor
public class SchoolDataAgreement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id") private School school;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "accepted_by_id") private SchoolUser acceptedBy;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 64) private String contentHash;
    @Column(nullable = false) private Instant acceptedAt;
    @Column(length = 45) private String ipAddress;

    public SchoolDataAgreement(School school, SchoolUser acceptedBy, int version, String contentHash,
                               Instant acceptedAt, String ipAddress) {
        this.school = school;
        this.acceptedBy = acceptedBy;
        this.version = version;
        this.contentHash = contentHash;
        this.acceptedAt = acceptedAt;
        this.ipAddress = ipAddress;
    }
}

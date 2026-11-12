package pulsoescolar_api.entity.terms;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity
@Table(name = "terms_revocations")
@Immutable
@Getter
@NoArgsConstructor
public class TermsRevocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private SchoolUser user;
    @Column(nullable = false) private Instant revokedAt;

    public TermsRevocation(SchoolUser user, Instant revokedAt) {
        this.user = user;
        this.revokedAt = revokedAt;
    }
}

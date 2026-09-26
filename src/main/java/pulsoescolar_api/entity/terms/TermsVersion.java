package pulsoescolar_api.entity.terms;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "terms_versions")
@Immutable
@Getter @NoArgsConstructor @AllArgsConstructor
public class TermsVersion {
    @Id private Long version;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;

    public static String label(long version) { return "1." + (version - 1); }
}

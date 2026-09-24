package pulsoescolar_api.entity.terms;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "terms_of_use")
@Getter @Setter @NoArgsConstructor
public class TermsOfUse {
    @Id private Long id;
    @Column(nullable = false) private long version;
    @Column(length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String content;
}

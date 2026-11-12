package pulsoescolar_api.entity.subject;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SubjectTopic {
    @Column(nullable = false, length = 40) private String title;
    @Column(nullable = false, length = 2000) private String description;
    @Column(name = "reference_text", nullable = false, length = 4000) private String references;
}

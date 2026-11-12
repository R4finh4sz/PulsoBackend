package pulsoescolar_api.entity.faq;

import jakarta.persistence.*;
import lombok.*;
import pulsoescolar_api.entity.user.Role;

@Entity
@Table(name = "faqs")
@Getter @Setter @NoArgsConstructor
public class Faq {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Role targetRole;
    @Column(nullable = false, length = 5000) private String message;
}

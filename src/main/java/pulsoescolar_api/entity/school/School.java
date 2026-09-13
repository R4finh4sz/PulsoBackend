package pulsoescolar_api.entity.school;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "schools")
@Getter @Setter @NoArgsConstructor
public class School {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String nome;
    @Column(nullable = false, unique = true, length = 18) private String cnpj;
    @Column(nullable = false, length = 255) private String logradouro;
    @Column(nullable = false, length = 100) private String bairro;
    @Column(nullable = false, length = 100) private String cidade;
}

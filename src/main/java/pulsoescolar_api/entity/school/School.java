package pulsoescolar_api.entity.school;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "schools")
@Getter @Setter @NoArgsConstructor
public class School {
    @Column(nullable = false) private boolean active = true;

    public void requireActive() {
        if (!active) throw new pulsoescolar_api.exception.common.BusinessConflictException("A escola está desativada.");
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String nome;
    @Column(nullable = false, length = 255) private String logradouro;
    @Column(nullable = false, length = 100) private String bairro;
    @Column(nullable = false, length = 100) private String cidade;
    @Column(length = 8) private String cep;
    @Column(length = 2) private String uf;
}

package pulsoescolar_api.entity.activity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity @Table(name = "quiz_questions")
@Getter @Setter @NoArgsConstructor
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 2000) private String text;
    @Column(length = 40) private String theme;
    @ElementCollection @CollectionTable(name = "quiz_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order") @Column(name = "text", nullable = false, length = 500)
    private List<String> options = new ArrayList<>();
    @Column(nullable = false) private int correctOption;
}

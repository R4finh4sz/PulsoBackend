package pulsoescolar_api.entity.activity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity @Table(name = "quiz_submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "student_id"}))
@Getter @Setter @NoArgsConstructor
public class QuizSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id") private SchoolUser student;
    @ElementCollection @CollectionTable(name = "quiz_answers", joinColumns = @JoinColumn(name = "submission_id"))
    @OrderColumn(name = "question_order") @Column(name = "selected_option", nullable = false)
    private List<Integer> answers = new ArrayList<>();
    @Column(nullable = false) private Instant submittedAt;
    private Instant gradedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "graded_by_id") private SchoolUser gradedBy;
    private Integer correctAnswers;
    @Column(precision = 5, scale = 2) private BigDecimal score;
}

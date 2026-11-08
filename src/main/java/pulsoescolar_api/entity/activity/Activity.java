package pulsoescolar_api.entity.activity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.user.SchoolUser;

@Entity @Table(name = "quiz_activities")
@Getter @Setter @NoArgsConstructor
public class Activity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subject_id") private Subject subject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id") private SchoolUser createdBy;
    private Instant closesAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private boolean recipientHistoryComplete;
    @ManyToMany
    @JoinTable(name = "activity_recipients", joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"activity_id", "student_id"}))
    private java.util.Set<SchoolUser> recipients = new java.util.HashSet<>();
    @OneToMany(cascade = CascadeType.PERSIST) @JoinColumn(name = "activity_id", nullable = false)
    @OrderColumn(name = "question_order")
    private List<QuizQuestion> questions = new ArrayList<>();
}

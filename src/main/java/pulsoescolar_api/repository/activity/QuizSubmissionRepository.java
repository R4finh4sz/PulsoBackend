package pulsoescolar_api.repository.activity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.activity.QuizSubmission;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    boolean existsByActivityIdAndStudentId(Long activityId, Long studentId);
    Optional<QuizSubmission> findByActivityIdAndStudentId(Long activityId, Long studentId);
    List<QuizSubmission> findByActivityIdOrderBySubmittedAtDescIdDesc(Long activityId);
    List<QuizSubmission> findByStudentIdOrderBySubmittedAtDescIdDesc(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSubmission s where s.id = :id")
    Optional<QuizSubmission> findForGrading(Long id);
}

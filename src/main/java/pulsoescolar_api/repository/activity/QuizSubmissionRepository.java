package pulsoescolar_api.repository.activity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.activity.QuizSubmission;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    interface ReportSubmission {
        Long getActivityId();
        Long getStudentId();
        java.time.Instant getGradedAt();
        java.math.BigDecimal getScore();
    }

    @Query("select s.activity.id as activityId, s.student.id as studentId, s.gradedAt as gradedAt, s.score as score "
            + "from QuizSubmission s where s.activity.id in :activityIds and s.student.deletedAt is null")
    List<ReportSubmission> findForReport(java.util.Collection<Long> activityIds);
    interface StudentRanking {
        Long getStudentId();
        String getStudentName();
        Long getTotalActivities();
        java.math.BigDecimal getTotalScore();
        Long getCorrectAnswers();
        Long getTotalQuestions();
    }

    @Query("""
            select s.student.id as studentId, s.student.fullName as studentName,
                   count(s) as totalActivities, sum(s.score) as totalScore,
                   sum(s.correctAnswers) as correctAnswers, sum(size(s.activity.questions)) as totalQuestions
            from QuizSubmission s
            where s.gradedAt is not null and s.score is not null and s.student.deletedAt is null
              and s.submittedAt >= :start and s.submittedAt < :end
              and s.activity.subject.classroom.school is not null
              and (:schoolId is null or s.activity.subject.classroom.school.id = :schoolId)
            group by s.student.id, s.student.fullName
            """)
    List<StudentRanking> rankStudents(java.time.Instant start, java.time.Instant end, Long schoolId);

    boolean existsByActivityIdAndStudentId(Long activityId, Long studentId);
    Optional<QuizSubmission> findByActivityIdAndStudentId(Long activityId, Long studentId);
    List<QuizSubmission> findByActivityIdAndStudentDeletedAtIsNullOrderBySubmittedAtDescIdDesc(Long activityId);
    List<QuizSubmission> findByStudentIdOrderBySubmittedAtDescIdDesc(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSubmission s where s.id = :id")
    Optional<QuizSubmission> findForGrading(Long id);
}

package pulsoescolar_api.service.activity;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.repository.activity.QuizSubmissionRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentRankingServiceTest {
    @Test
    void ordersByAccuracyCorrectAnswersActivitiesThenPortugueseAlphabetWithoutBreakingTiedPositions() {
        var submissions = mock(QuizSubmissionRepository.class);
        var service = new StudentRankingService(submissions, mock(SchoolRepository.class));
        doReturn(List.of(
                row(6, "Bruno", 1, 8, 10), row(5, "Álvaro", 1, 8, 10),
                row(4, "Zélia", 2, 8, 10), row(3, "Zeca", 2, 16, 20),
                row(2, "Zoe", 10, 90, 100), row(1, "Zara", 1, 1, 1)))
                .when(submissions).rankStudents(any(), any(), isNull());
        var result = service.ranking(2026, 1, null);
        assertThat(result).extracting(r -> r.studentId()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(result).extracting(r -> r.position()).containsExactly(1, 2, 3, 4, 5, 5);
        assertThat(result.getFirst().accuracyPercentage()).isEqualByComparingTo("100");
        assertThat(result.get(2).correctAnswers()).isEqualTo(16);
        assertThat(result.get(2).totalQuestions()).isEqualTo(20);
    }

    @Test
    void comparesPercentagesBeforeRoundingForDisplay() {
        var submissions = mock(QuizSubmissionRepository.class);
        var service = new StudentRankingService(submissions, mock(SchoolRepository.class));
        doReturn(List.of(row(1, "Ana", 2, 1600000, 2000000), row(2, "Zeca", 1, 800001, 1000000)))
                .when(submissions).rankStudents(any(), any(), isNull());
        var result = service.ranking(2026, 1, null);
        assertThat(result).extracting(r -> r.studentId()).containsExactly(2L, 1L);
        assertThat(result).extracting(r -> r.position()).containsExactly(1, 2);
        assertThat(result.get(0).accuracyPercentage()).isEqualByComparingTo(result.get(1).accuracyPercentage());
    }

    private QuizSubmissionRepository.StudentRanking row(long id, String name, long activities, long correct, long questions) {
        var row = mock(QuizSubmissionRepository.StudentRanking.class);
        when(row.getStudentId()).thenReturn(id);
        when(row.getStudentName()).thenReturn(name);
        when(row.getTotalActivities()).thenReturn(activities);
        when(row.getTotalScore()).thenReturn(BigDecimal.valueOf(80 * activities));
        when(row.getCorrectAnswers()).thenReturn(correct);
        when(row.getTotalQuestions()).thenReturn(questions);
        return row;
    }
}

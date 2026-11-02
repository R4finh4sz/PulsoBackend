package pulsoescolar_api.mapper.activity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pulsoescolar_api.dto.activity.ActivityStatus;
import pulsoescolar_api.entity.activity.Activity;
import pulsoescolar_api.entity.activity.QuizQuestion;
import pulsoescolar_api.entity.activity.QuizSubmission;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.dto.activity.ThemePerformanceResponse;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.subject.Subject;
import static org.assertj.core.api.Assertions.assertThat;

class ActivityMapperTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    QuizSubmission submission() {
        var activity = new Activity(); activity.setId(1L);
        var themes = List.of("Função afim", "Gráficos", "Coeficientes", "Função afim", "Gráficos",
                "Coeficientes", "Função afim", "Gráficos", "Coeficientes", "Função afim");
        for (String theme : themes) {
            var question = new QuizQuestion(); question.setTheme(theme); question.setCorrectOption(1);
            activity.getQuestions().add(question);
        }
        var submission = new QuizSubmission(); submission.setActivity(activity);
        var student = new SchoolUser(); student.setId(2L); submission.setStudent(student);
        submission.getAnswers().addAll(List.of(1, 1, 1, 1, 1, 0, 1, 0, 0, 1));
        return submission;
    }

    @Test
    void gradedResultGroupsInterleavedQuestionsAndIdentifiesThemesToReview() {
        var submission = submission(); submission.setGradedAt(NOW);
        var result = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(submission);
        assertThat(result.themePerformance()).containsExactly(
                new ThemePerformanceResponse("Função afim", 4, 4, 0, new BigDecimal("100.00"), false),
                new ThemePerformanceResponse("Gráficos", 3, 2, 1, new BigDecimal("66.67"), true),
                new ThemePerformanceResponse("Coeficientes", 3, 1, 2, new BigDecimal("33.33"), true));
    }

    @Test
    void ungradedResultDoesNotExposePerformanceOrRevealAnswerKey() {
        var result = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(submission());
        assertThat(result.themePerformance()).isEmpty();
    }

    @Test
    void historicalQuestionsWithoutThemeAreNotReclassified() {
        var submission = submission(); submission.setGradedAt(NOW);
        submission.getActivity().getQuestions().forEach(question -> question.setTheme(null));
        assertThat(new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(submission).themePerformance()).isEmpty();
    }

    static Stream<Arguments> deadlines() {
        return Stream.of(Arguments.of(null, ActivityStatus.OPEN),
                Arguments.of(NOW.plusSeconds(1), ActivityStatus.OPEN),
                Arguments.of(NOW, ActivityStatus.FINISHED),
                Arguments.of(NOW.minusSeconds(1), ActivityStatus.FINISHED));
    }

    @ParameterizedTest @MethodSource("deadlines")
    void statusReflectsDeadlineIncludingExactClosingInstant(Instant closesAt, ActivityStatus expected) {
        var classroom = new Classroom(); classroom.setId(1L);
        var subject = new Subject(); subject.setId(2L); subject.setClassroom(classroom);
        var activity = new Activity(); activity.setId(3L); activity.setSubject(subject); activity.setClosesAt(closesAt);
        var mapper = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(mapper.toResponse(activity).status()).isEqualTo(expected);
        assertThat(mapper.toQuiz(activity).activity().status()).isEqualTo(expected);
    }
}

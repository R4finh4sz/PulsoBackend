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
    void shufflesPerStudentWithoutChangingOriginalOrderAndKeepsLegacyQuizzesUnchanged() {
        var activity = submission().getActivity();
        var mapper = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC));
        var original = java.util.stream.IntStream.range(0, 10).boxed().toList();
        var first = mapper.questionOrder(activity, 2L);
        assertThat(first).containsExactlyInAnyOrderElementsOf(original).isNotEqualTo(original);
        assertThat(mapper.questionOrder(activity, 2L)).isEqualTo(first);
        assertThat(mapper.questionOrder(activity, 3L)).isNotEqualTo(first);
        assertThat(mapper.questionOrder(activity, null)).isEqualTo(original);
        activity.setQuestionsShuffled(false);
        assertThat(mapper.questionOrder(activity, 2L)).isEqualTo(original);
        assertThat(mapper.questionOrder(activity, 3L)).isEqualTo(original);
    }

    @Test
    void studentAnswersFollowDisplayedOrderWhileTeacherAnswersAndThemesKeepOriginalOrder() {
        var submission = submission(); submission.setGradedAt(NOW);
        var mapper = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC));
        var original = List.copyOf(submission.getAnswers());
        var studentResult = mapper.toResponse(submission, 2L);
        var teacherResult = mapper.toResponse(submission);
        assertThat(studentResult.answers()).containsExactlyElementsOf(mapper.questionOrder(submission.getActivity(), 2L)
                .stream().map(original::get).toList());
        assertThat(teacherResult.answers()).isEqualTo(original);
        assertThat(studentResult.themePerformance()).isEqualTo(teacherResult.themePerformance());
        assertThat(submission.getAnswers()).isEqualTo(original);
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

    @Test
    void activityYearUsesCreationDateInBrazilInsteadOfUtcYearOrClientInput() {
        var classroom = new Classroom(); classroom.setId(1L);
        var subject = new Subject(); subject.setId(2L); subject.setClassroom(classroom);
        var activity = new Activity(); activity.setId(3L); activity.setSubject(subject);
        activity.setCreatedAt(Instant.parse("2027-01-01T02:59:59Z"));
        assertThat(new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(activity).year()).isEqualTo(2026);
    }

    @ParameterizedTest @MethodSource("deadlines")
    void statusReflectsDeadlineIncludingExactClosingInstant(Instant closesAt, ActivityStatus expected) {
        var classroom = new Classroom(); classroom.setId(1L);
        var subject = new Subject(); subject.setId(2L); subject.setClassroom(classroom);
        var activity = new Activity(); activity.setId(3L); activity.setSubject(subject); activity.setClosesAt(closesAt);
        activity.setCreatedAt(NOW);
        var mapper = new ActivityMapper(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(mapper.toResponse(activity).status()).isEqualTo(expected);
        assertThat(mapper.toQuiz(activity).activity().status()).isEqualTo(expected);
    }
}

package pulsoescolar_api.mapper.activity;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.activity.*;
import pulsoescolar_api.entity.activity.*;

@Component @RequiredArgsConstructor
public class ActivityMapper {
    private final Clock clock;
    public ActivityResponse toResponse(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getSubject().getId(),
                activity.getSubject().getClassroom().getId(),
                activity.getSubject().getClassroom().getSchool() == null ? null : activity.getSubject().getClassroom().getSchool().getId(),
                activity.getQuestions().size(),
                activity.getCreatedAt(), activity.getClosesAt(),
                activity.getClosesAt() == null || clock.instant().isBefore(activity.getClosesAt())
                        ? ActivityStatus.OPEN : ActivityStatus.FINISHED,
                activity.getCreatedAt().atZone(java.time.ZoneId.of("America/Sao_Paulo")).getYear());
    }

    public QuizResponse toQuiz(Activity activity) {
        return toQuiz(activity, null);
    }

    public List<Integer> questionOrder(Activity activity, Long studentId) {
        var order = new ArrayList<>(java.util.stream.IntStream.range(0, activity.getQuestions().size()).boxed().toList());
        if (activity.isQuestionsShuffled() && studentId != null) {
            java.util.Collections.shuffle(order, new java.util.Random(31L * activity.getId() + studentId));
        }
        return order;
    }

    public QuizResponse toQuiz(Activity activity, Long studentId) {
        return new QuizResponse(toResponse(activity), questionOrder(activity, studentId).stream()
                .map(activity.getQuestions()::get)
                .map(q -> new QuizResponse.Question(q.getId(), q.getText(), List.copyOf(q.getOptions()), q.getTheme())).toList());
    }

    public SubmissionResponse toResponse(QuizSubmission submission) {
        return toResponse(submission, null);
    }

    public SubmissionResponse toResponse(QuizSubmission submission, Long studentId) {
        var answers = studentId != null && submission.getActivity().isQuestionsShuffled()
                ? questionOrder(submission.getActivity(), studentId).stream().map(submission.getAnswers()::get).toList()
                : List.copyOf(submission.getAnswers());
        return new SubmissionResponse(submission.getId(), submission.getActivity().getId(),
                submission.getStudent().getId(), answers, submission.getSubmittedAt(),
                submission.getGradedAt(), submission.getCorrectAnswers(),
                submission.getActivity().getQuestions().size(), submission.getScore(), themePerformance(submission));
    }

    private List<ThemePerformanceResponse> themePerformance(QuizSubmission submission) {
        if (submission.getGradedAt() == null) return List.of();
        Map<String, List<Boolean>> byTheme = new LinkedHashMap<>();
        var questions = submission.getActivity().getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            // Questions created before V28 may have no theme; do not invent a historical classification.
            if (question.getTheme() == null) continue;
            byTheme.computeIfAbsent(question.getTheme(), theme -> new ArrayList<>())
                    .add(submission.getAnswers().get(i) == question.getCorrectOption());
        }
        return byTheme.entrySet().stream().map(entry -> {
            int total = entry.getValue().size();
            int correct = (int) entry.getValue().stream().filter(Boolean::booleanValue).count();
            int incorrect = total - correct;
            var percentage = BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            return new ThemePerformanceResponse(entry.getKey(), total, correct, incorrect, percentage, incorrect > 0);
        }).toList();
    }
}

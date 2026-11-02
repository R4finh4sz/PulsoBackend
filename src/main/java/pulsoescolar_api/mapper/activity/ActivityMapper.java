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
                        ? ActivityStatus.OPEN : ActivityStatus.FINISHED);
    }

    public QuizResponse toQuiz(Activity activity) {
        return new QuizResponse(toResponse(activity), activity.getQuestions().stream()
                .map(q -> new QuizResponse.Question(q.getId(), q.getText(), List.copyOf(q.getOptions()), q.getTheme())).toList());
    }

    public SubmissionResponse toResponse(QuizSubmission submission) {
        return new SubmissionResponse(submission.getId(), submission.getActivity().getId(),
                submission.getStudent().getId(), List.copyOf(submission.getAnswers()), submission.getSubmittedAt(),
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

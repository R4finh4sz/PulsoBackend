package pulsoescolar_api.dto.activity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SubmissionResponse(Long id, Long activityId, Long studentId, List<Integer> answers,
        Instant submittedAt, Instant gradedAt, Integer correctAnswers, int questionCount, BigDecimal score,
        List<ThemePerformanceResponse> themePerformance) {}

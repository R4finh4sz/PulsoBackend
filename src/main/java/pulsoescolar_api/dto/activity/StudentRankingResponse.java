package pulsoescolar_api.dto.activity;

import java.math.BigDecimal;

public record StudentRankingResponse(int position, Long studentId, String studentName,
        long totalActivities, BigDecimal totalScore, BigDecimal averageScore,
        long correctAnswers, long totalQuestions, BigDecimal accuracyPercentage) {}

package pulsoescolar_api.dto.activity;

import java.math.BigDecimal;

public record ThemePerformanceResponse(String theme, int questionCount, int correctAnswers,
        int incorrectAnswers, BigDecimal percentage, boolean needsReview) {}

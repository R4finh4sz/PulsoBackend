package pulsoescolar_api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Integer maxRequests, Duration window, Integer authMaxRequests, Duration authWindow) {
    public RateLimitProperties {
        if (maxRequests == null) maxRequests = 120;
        if (window == null) window = Duration.ofMinutes(1);
        if (authMaxRequests == null) authMaxRequests = 10;
        if (authWindow == null) authWindow = Duration.ofMinutes(1);
        if (maxRequests < 1 || authMaxRequests < 1 || window.compareTo(Duration.ofSeconds(1)) < 0
                || authWindow.compareTo(Duration.ofSeconds(1)) < 0 || window.compareTo(Duration.ofDays(1)) > 0
                || authWindow.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Rate limit exige quantidades positivas e janelas entre 1s e 24h.");
        }
    }
}

package pulsoescolar_api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record JwtProperties(String jwtSecret, Duration sessionTtl) {
    public JwtProperties {
        if (sessionTtl == null || sessionTtl.compareTo(Duration.ofMinutes(1)) < 0
                || sessionTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("app.auth.session-ttl deve estar entre 1m e 24h.");
        }
    }

    @Override public String toString() { return "JwtProperties[jwtSecret=REDACTED, sessionTtl=" + sessionTtl + "]"; }
}

package pulsoescolar_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(String apiKey, String from) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && from != null && !from.isBlank();
    }

    @Override public String toString() { return "ResendProperties[REDACTED]"; }
}

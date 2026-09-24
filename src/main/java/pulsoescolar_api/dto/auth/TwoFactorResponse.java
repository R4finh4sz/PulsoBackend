package pulsoescolar_api.dto.auth;
import java.time.Instant;
public record TwoFactorResponse(boolean twoFactorRequired, Instant codeExpiresAt, Instant resendAvailableAt) {}

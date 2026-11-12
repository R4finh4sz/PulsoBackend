package pulsoescolar_api.dto.auth;

import java.time.Instant;

public record RecoveryVerificationResponse(String resetToken, Instant expiresAt) {
    @Override public String toString() { return "RecoveryVerificationResponse[REDACTED]"; }
}

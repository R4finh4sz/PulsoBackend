package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.*;

public record PasswordRecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
    @Override public String toString() { return "PasswordRecoveryRequest[REDACTED]"; }
}

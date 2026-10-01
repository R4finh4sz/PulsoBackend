package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.*;

public record VerifyRecoveryCodeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    @Override public String toString() { return "VerifyRecoveryCodeRequest[REDACTED]"; }
}

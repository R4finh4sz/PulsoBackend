package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String resetToken,
        @NotBlank @Size(max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmPassword) {
    @Override public String toString() { return "ResetPasswordRequest[REDACTED]"; }
}

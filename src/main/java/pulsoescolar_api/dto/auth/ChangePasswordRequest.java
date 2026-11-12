package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword) {
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}

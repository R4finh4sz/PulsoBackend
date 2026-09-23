package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword,
        @NotNull @AssertTrue(message = "É necessário aceitar os termos.") Boolean termsAccepted) {
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}

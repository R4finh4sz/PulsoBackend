package pulsoescolar_api.dto.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record TwoFactorSettingsRequest(@NotNull Boolean enabled, @NotBlank String currentPassword) {
    @Override public String toString() { return "TwoFactorSettingsRequest[REDACTED]"; }
}

package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;

public record CreateDeletionRequest(@NotBlank @Size(max = 2000) String reason, @NotBlank String currentPassword) {
    @Override public String toString() { return "CreateDeletionRequest[REDACTED]"; }
}

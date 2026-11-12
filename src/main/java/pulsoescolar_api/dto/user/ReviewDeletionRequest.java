package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;
import pulsoescolar_api.entity.user.DeletionStatus;

public record ReviewDeletionRequest(@NotNull DeletionStatus status,
        @NotBlank @Size(max = 2000) String reason) {}

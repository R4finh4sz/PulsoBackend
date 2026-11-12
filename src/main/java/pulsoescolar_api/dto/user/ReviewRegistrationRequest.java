package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;
import pulsoescolar_api.entity.user.RegistrationStatus;

public record ReviewRegistrationRequest(@NotNull RegistrationStatus status, @Size(max = 2000) String reason) {}

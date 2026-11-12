package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;
import pulsoescolar_api.entity.user.RegistrationStatus;

public record ReviewStudentTransferRequest(@NotNull RegistrationStatus status, @Positive Long classroomId) {}

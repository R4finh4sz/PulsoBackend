package pulsoescolar_api.dto.school;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateSchoolCoordinatorRequest(@NotNull @Positive Long coordinatorId) {}

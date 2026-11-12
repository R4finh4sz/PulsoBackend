package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ActiveSchoolRequest(@NotNull @Positive Long schoolId) {}

package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;

public record TransferStudentRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 50) String ra,
        @NotNull @Positive Long schoolId) {}

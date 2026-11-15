package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CorrectBirthDateRequest(@NotNull @Past LocalDate birthDate, @NotBlank @Size(max = 2000) String reason) {}

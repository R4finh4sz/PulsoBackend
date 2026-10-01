package pulsoescolar_api.dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import pulsoescolar_api.entity.user.Role;

// Location is used by the frontend to find the school, never persisted with the person.
@JsonIgnoreProperties({"cep", "city", "state", "address", "confirmPassword"})
public record SelfRegistrationRequest(
        @JsonAlias("fullName") @NotBlank @Size(max = 50)
        @Pattern(regexp = ".*\\S+\\s+\\S+.*", message = "Informe nome e sobrenome.") String name,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9]+") String ra,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull @Positive Long schoolId,
        @NotNull @AssertTrue Boolean termsAccepted,
        @NotBlank @Pattern(regexp = "1\\.[0-9]+") String termsVersion,
        Role role) {
    @Override public String toString() { return "SelfRegistrationRequest[data=REDACTED]"; }
}

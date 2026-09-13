package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "O nome não pode ser vazio.")
        @Size(max = 100) String fullName,
        @Pattern(regexp = "(?s).*\\S.*", message = "O RA não pode ser vazio.")
        @Size(max = 50) String ra,
        @Pattern(regexp = "(?s).*\\S.*", message = "O e-mail não pode ser vazio.")
        @Email @Size(max = 50) String email) {}

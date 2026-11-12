package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @jakarta.validation.constraints.Null(message = "O nome não pode ser alterado.") String fullName,
        @jakarta.validation.constraints.Null(message = "O RA não pode ser alterado.") String ra,
        @Pattern(regexp = "(?s).*\\S.*", message = "O e-mail não pode ser vazio.")
        @Email @Size(max = 254) String email) {}

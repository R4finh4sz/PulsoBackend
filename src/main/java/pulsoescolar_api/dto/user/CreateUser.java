package pulsoescolar_api.dto.user;
import jakarta.validation.constraints.*;
public record CreateUser(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 50) String ra,
        @NotBlank @Email @Size(max = 50) String email) {}

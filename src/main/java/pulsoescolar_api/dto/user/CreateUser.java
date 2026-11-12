package pulsoescolar_api.dto.user;
import jakarta.validation.constraints.*;
public record CreateUser(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 50) String ra,
        @NotBlank @Email @Size(max = 50) String email,
        @Positive Long schoolId) {
    public CreateUser(String fullName, String ra, String email) {
        this(fullName, ra, email, null);
    }
}

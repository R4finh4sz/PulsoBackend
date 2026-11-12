package pulsoescolar_api.dto.auth;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
    @Override public String toString() { return "LoginRequest[credentials=REDACTED]"; }
}

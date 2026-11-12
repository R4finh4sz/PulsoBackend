package pulsoescolar_api.dto.auth;
import jakarta.validation.constraints.*;
public record VerifyCodeRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    @Override public String toString() { return "VerifyCodeRequest[REDACTED]"; }
}

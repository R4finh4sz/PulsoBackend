package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record VerifyInvitationRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    @Override public String toString() { return "VerifyInvitationRequest[REDACTED]"; }
}

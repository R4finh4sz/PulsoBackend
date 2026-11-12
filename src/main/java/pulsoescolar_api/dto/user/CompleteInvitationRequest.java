package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public record CompleteInvitationRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = ".*\\S+\\s+\\S+.*") String name,
        @NotBlank @Pattern(regexp = "[0-9]+") @Size(max = 50) String ra,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "(?s)(?=.*[A-Z])(?=.*[0-9]).*") String password,
        @NotNull @AssertTrue Boolean termsAccepted,
        @NotBlank @Pattern(regexp = "1\\.[0-9]+") String termsVersion) {
    @Override public String toString() { return "CompleteInvitationRequest[REDACTED]"; }
}

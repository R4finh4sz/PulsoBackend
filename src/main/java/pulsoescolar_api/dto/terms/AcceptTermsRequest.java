package pulsoescolar_api.dto.terms;
import jakarta.validation.constraints.*;
public record AcceptTermsRequest(@NotBlank @Pattern(regexp = "1\\.[0-9]+") String version,
        @NotNull @AssertTrue Boolean termsAccepted) {}

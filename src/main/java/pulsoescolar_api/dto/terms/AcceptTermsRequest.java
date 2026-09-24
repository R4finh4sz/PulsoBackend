package pulsoescolar_api.dto.terms;
import jakarta.validation.constraints.*;
public record AcceptTermsRequest(@NotNull @Positive Long version,
        @NotNull @AssertTrue Boolean termsAccepted) {}

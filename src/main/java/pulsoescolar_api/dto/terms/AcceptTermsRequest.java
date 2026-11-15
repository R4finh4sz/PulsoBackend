package pulsoescolar_api.dto.terms;
import jakarta.validation.constraints.*;

/** Students accept STUDENT_TERM; other roles accept general terms. School roles also accept privacy. */
public record AcceptTermsRequest(@NotBlank @Pattern(regexp = "1\\.[0-9]+") String version,
        @NotNull @AssertTrue Boolean termsAccepted,
        Boolean privacyAccepted,
        @Pattern(regexp = "1\\.[0-9]+") String privacyVersion) {
    public AcceptTermsRequest(String version, Boolean termsAccepted) {
        this(version, termsAccepted, null, null);
    }
}

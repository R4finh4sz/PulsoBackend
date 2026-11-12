package pulsoescolar_api.dto.terms;
import jakarta.validation.constraints.*;
public record TermsRequest(@NotBlank @Size(max = 200) String title, @NotBlank String content) {}

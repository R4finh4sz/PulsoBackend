package pulsoescolar_api.dto.agreement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class DataAgreementDtos {
    private DataAgreementDtos() {}

    /** The current agreement published by the admin and whether the coordinator's school accepted it. */
    public record Response(String version, String title, String content, boolean accepted, Instant acceptedAt) {}

    public record AcceptRequest(@NotBlank @Pattern(regexp = "1\\.[0-9]+") String version,
                                @NotNull @AssertTrue Boolean accepted) {}
}

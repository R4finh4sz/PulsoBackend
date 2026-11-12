package pulsoescolar_api.dto.subject;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.Locale;

public record CreateSubjectInvitationRequest(
        @JsonDeserialize(using = SubjectStringDeserializer.class) @NotBlank @Email @Size(max = 254) String email) {
    public CreateSubjectInvitationRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}

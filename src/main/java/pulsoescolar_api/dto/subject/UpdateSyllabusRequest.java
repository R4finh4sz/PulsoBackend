package pulsoescolar_api.dto.subject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import pulsoescolar_api.dto.subject.CreateSubjectRequest.Topic;

public record UpdateSyllabusRequest(
        @NotNull @Size(min = 1, max = 15) List<@NotNull @Valid Topic> topics) {}

package pulsoescolar_api.dto.subject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateSubjectRequest(
        @JsonDeserialize(using = SubjectStringDeserializer.class) @NotBlank @Size(max = 100) String name,
        @JsonDeserialize(using = SubjectStringDeserializer.class) @NotNull @Size(max = 5000) String syllabus,
        @NotNull @Size(min = 1, max = 15) List<@NotNull @Valid Topic> topics) {
    public CreateSubjectRequest {
        name = SubjectText.trim(name);
        syllabus = SubjectText.trim(syllabus);
    }

    public record Topic(@JsonDeserialize(using = SubjectStringDeserializer.class) @ThemeName String title,
                        @JsonDeserialize(using = SubjectStringDeserializer.class) @NotBlank @Size(max = 2000) String description,
                        @JsonDeserialize(using = SubjectStringDeserializer.class) @NotNull @Size(max = 4000) String references) {
        public Topic {
            title = SubjectText.trim(title);
            description = SubjectText.trim(description);
            references = SubjectText.trim(references);
        }
    }
}

package pulsoescolar_api.dto.subject;

import tools.jackson.databind.annotation.JsonDeserialize;

public record ThemeRequest(@JsonDeserialize(using = SubjectStringDeserializer.class) @ThemeName String name) {
    public ThemeRequest {
        name = SubjectText.trim(name);
    }
}

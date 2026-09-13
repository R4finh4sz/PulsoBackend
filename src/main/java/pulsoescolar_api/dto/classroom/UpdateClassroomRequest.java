package pulsoescolar_api.dto.classroom;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateClassroomRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "O nome não pode ser vazio.")
        @Size(max = 100) String name,
        @Pattern(regexp = "[A-Z]", message = "O identificador deve ser uma letra de A a Z.")
        String identifier) {}

package pulsoescolar_api.dto.classroom;

import jakarta.validation.constraints.Pattern;

public record UpdateClassroomRequest(
        @Pattern(regexp = "^[1-3]º ano · (Manhã|Tarde|Noite|Integral)$",
                message = "O nome da turma deve seguir o formato: 1º, 2º ou 3º ano · Manhã, Tarde, Noite ou Integral.") String name,
        @Pattern(regexp = "[A-Z]", message = "O identificador deve ser uma letra de A a Z.")
        String identifier) {}

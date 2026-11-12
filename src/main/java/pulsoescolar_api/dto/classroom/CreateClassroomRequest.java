package pulsoescolar_api.dto.classroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateClassroomRequest(
        @NotBlank(message = "O nome da sala é obrigatório.")
        @Pattern(regexp = "^[1-3]º ano · (Manhã|Tarde|Noite|Integral)$",
                message = "O nome da turma deve seguir o formato: 1º, 2º ou 3º ano · Manhã, Tarde, Noite ou Integral.") String name,
        @NotBlank(message = "O identificador da sala é obrigatório.")
        @Pattern(regexp = "[A-Z]", message = "O identificador da sala deve conter exatamente uma letra maiúscula de A a Z.")
        String identifier) {}

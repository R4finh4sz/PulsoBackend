package pulsoescolar_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClassroomRequest(
        @NotBlank(message = "O nome da sala é obrigatório.")
        @Size(max = 100, message = "O nome da sala deve ter no máximo 100 caracteres.") String name,
        @NotBlank(message = "O identificador da sala é obrigatório.")
        @Pattern(regexp = "[A-Z]", message = "O identificador da sala deve conter exatamente uma letra maiúscula de A a Z.")
        String identifier) {}

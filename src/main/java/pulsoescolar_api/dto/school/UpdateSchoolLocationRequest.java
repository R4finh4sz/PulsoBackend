package pulsoescolar_api.dto.school;

import jakarta.validation.constraints.*;

public record UpdateSchoolLocationRequest(
        @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String uf) {}

package pulsoescolar_api.dto.school;

import jakarta.validation.constraints.*;

public record CreateSchoolRequest(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Pattern(regexp = "(?:[0-9]{14}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})",
                message = "Informe 14 dígitos, com ou sem máscara.") String cnpj,
        @NotBlank @Size(max = 255) String logradouro,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade) {}

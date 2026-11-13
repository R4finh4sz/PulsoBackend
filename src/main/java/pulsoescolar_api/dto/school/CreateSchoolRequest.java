package pulsoescolar_api.dto.school;

import jakarta.validation.constraints.*;

public record CreateSchoolRequest(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Size(max = 255) String logradouro,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String cep,
        @NotNull BrazilianState uf) {}

package pulsoescolar_api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record CreateUser(
        @NotBlank(message = "O nome completo é obrigatório.")
        @Size(max = 200, message = "O nome completo deve ter no máximo 200 caracteres.") String fullName,
        @NotBlank(message = "O RA é obrigatório.")
        @Size(max = 50, message = "O RA deve ter no máximo 50 caracteres.") String ra,
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.") String email,
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 12, max = 72, message = "A senha deve ter entre 12 e 72 caracteres.") String password) {

    @AssertTrue(message = "A senha deve ter no máximo 72 bytes em UTF-8.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}

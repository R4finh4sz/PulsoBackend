package pulsoescolar_api.dto.faq;

import jakarta.validation.constraints.*;
import pulsoescolar_api.entity.user.Role;

public record FaqRequest(
        @NotBlank(message = "Informe o título.") @Size(max = 200) String title,
        @NotNull(message = "Informe o perfil destinatário.") Role targetRole,
        @NotBlank(message = "Informe a mensagem.") @Size(max = 5000) String message) {
    public FaqRequest {
        title = title == null ? null : title.strip();
        message = message == null ? null : message.strip();
    }
}

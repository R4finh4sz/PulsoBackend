package pulsoescolar_api.service.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pulsoescolar_api.exception.EmailDeliveryException;

@Service
@RequiredArgsConstructor
public class TwoFactorMailService {
    private final EmailSender sender;

    public void send(String email, String code) {
        try {
            sender.send(email, "Código de autenticação PulsoEscolar",
                    "Código: " + code + "\n\nVálido por 10 minutos. Não compartilhe este código.\n\nEquipe PulsoEscolar");
        } catch (EmailDeliveryException ex) {
            throw new EmailDeliveryException("Não foi possível enviar o código de autenticação por e-mail. Tente novamente.");
        }
    }
}

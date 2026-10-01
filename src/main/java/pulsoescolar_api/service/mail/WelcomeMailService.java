package pulsoescolar_api.service.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pulsoescolar_api.exception.EmailDeliveryException;

@Service
@RequiredArgsConstructor
public class WelcomeMailService {
    private final EmailSender sender;

    public void send(String email, String fullName, String password) {
        try {
            sender.send(email, "Seu acesso ao Pulso Escolar",
                    "Olá, " + fullName + "!\n\nSeu acesso ao Pulso Escolar foi criado.\n"
                            + "E-mail: " + email + "\nSenha: " + password
                            + "\n\nUse essas credenciais para entrar. Não compartilhe sua senha.");
        } catch (EmailDeliveryException ex) {
            throw new EmailDeliveryException();
        }
    }
}

package pulsoescolar_api.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvitationMailService {
    private final EmailSender sender;
    private final String baseUrl;
    private final String from;

    public InvitationMailService(EmailSender sender,
            @Value("${app.invitation.base-url:http://localhost:8080/api/convite}") String baseUrl,
            @Value("${app.invitation.from:}") String from) {
        this.sender = sender;
        this.baseUrl = baseUrl;
        this.from = from;
    }

    public void sendInvitation(String email, String token) {
        sender.send(email, "Convite para o Pulso Escolar",
                "Acesse seu convite para criar a conta: " + baseUrl + "/" + token
                        + "\n\nEste link é válido por 48 horas a partir do envio do convite."
                        + " Após esse prazo, entre em contato com suportePulso@gmail.com para solicitar um novo convite.");
    }

    public void sendCode(String email, String code) {
        sender.send(email, "Código de verificação do convite", "Código: " + code + "\n\nVálido por 10 minutos.");
    }
}

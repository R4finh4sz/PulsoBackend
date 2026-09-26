package pulsoescolar_api.service.mail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pulsoescolar_api.exception.EmailDeliveryException;

@Service
public class TwoFactorMailService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TwoFactorMailService.class);
    private static final String DELIVERY_ERROR = "Não foi possível enviar o código de autenticação por e-mail. Tente novamente.";
    private final JavaMailSender sender;
    private final String from;
    public TwoFactorMailService(JavaMailSender sender, @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.from = from;
    }
    public void send(String email, String code) {
        if (from.isBlank()) {
            log.warn("Envio de 2FA indisponível: app.mail.from não configurado.");
            throw new EmailDeliveryException(DELIVERY_ERROR);
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Código de autenticação - Pulso Escolar");
        message.setText("Código: " + code + "\n\nVálido por 10 minutos. Não compartilhe este código.");
        try { sender.send(message); }
        catch (MailException ex) {
            Throwable cause = ex.getMostSpecificCause();
            log.warn("Falha no envio de 2FA: tipo={}, causa={}",
                    ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
            throw new EmailDeliveryException(DELIVERY_ERROR);
        }
    }
}

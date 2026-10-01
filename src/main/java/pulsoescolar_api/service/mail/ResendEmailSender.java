package pulsoescolar_api.service.mail;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pulsoescolar_api.config.ResendProperties;
import pulsoescolar_api.exception.EmailDeliveryException;

@Component
@RequiredArgsConstructor
public class ResendEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String DELIVERY_ERROR = "Não foi possível enviar o e-mail. Tente novamente.";
    private final Emails emails;
    private final ResendProperties properties;

    @Override
    public void send(String recipient, String subject, String text) {
        if (!properties.configured()) {
            log.warn("Envio indisponível: configure RESEND_API_KEY e RESEND_FROM_EMAIL.");
            throw new EmailDeliveryException(DELIVERY_ERROR);
        }
        var message = CreateEmailOptions.builder()
                .from(properties.from())
                .to(recipient)
                .subject(subject)
                .text(text)
                .build();
        try {
            emails.send(message);
        } catch (ResendException | RuntimeException ex) {
            // Provider responses can contain message content or credentials.
            log.warn("Falha no envio pelo Resend: tipo={}", ex.getClass().getSimpleName());
            throw new EmailDeliveryException(DELIVERY_ERROR);
        }
    }
}

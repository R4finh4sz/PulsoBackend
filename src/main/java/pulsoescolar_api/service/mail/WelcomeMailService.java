package pulsoescolar_api.service.mail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pulsoescolar_api.exception.EmailDeliveryException;
@Service
public class WelcomeMailService {
    private final JavaMailSender sender;
    private final String from;
    public WelcomeMailService(JavaMailSender sender, @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.from = from;
    }
    public void send(String email, String fullName, String password) {
        if (from.isBlank()) throw new EmailDeliveryException();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Seu acesso ao Pulso Escolar");
        message.setText("Olá, " + fullName + "!\n\nSeu acesso ao Pulso Escolar foi criado.\n"
                + "E-mail: " + email + "\nSenha: " + password
                + "\n\nUse essas credenciais para entrar. Não compartilhe sua senha.");
        try {
            sender.send(message);
        } catch (MailException ex) {
            // Never expose SMTP credentials or the message containing the password.
            throw new EmailDeliveryException();
        }
    }
}

package pulsoescolar_api.service.mail;

public interface EmailSender {
    void send(String recipient, String subject, String text);
}

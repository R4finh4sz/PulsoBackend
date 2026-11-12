package pulsoescolar_api.service.mail;

public record NotificationMailRequested(String email, String subject, String text) {
    @Override public String toString() { return "NotificationMailRequested[REDACTED]"; }
}

package pulsoescolar_api.service.mail;

public record TwoFactorMailRequested(String email, String code) {
    @Override
    public String toString() {
        return "TwoFactorMailRequested[REDACTED]";
    }
}

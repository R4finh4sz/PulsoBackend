package pulsoescolar_api.exception.auth;

public class VerificationCooldownException extends RuntimeException {
    public VerificationCooldownException(String message) {
        super(message);
    }
}

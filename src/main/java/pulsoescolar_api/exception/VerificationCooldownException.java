package pulsoescolar_api.exception;

public class VerificationCooldownException extends RuntimeException {
    public VerificationCooldownException(String message) {
        super(message);
    }
}

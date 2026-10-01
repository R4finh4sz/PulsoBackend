package pulsoescolar_api.exception;

public class InvalidVerificationCodeException extends BusinessValidationException {
    public InvalidVerificationCodeException() {
        super("Código inválido.");
    }
}

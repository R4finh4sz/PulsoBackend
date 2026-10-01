package pulsoescolar_api.exception;

public class VerificationCodeUnavailableException extends BusinessValidationException {
    public VerificationCodeUnavailableException() {
        super("Código expirado ou limite de tentativas atingido. Solicite reenvio.");
    }
}

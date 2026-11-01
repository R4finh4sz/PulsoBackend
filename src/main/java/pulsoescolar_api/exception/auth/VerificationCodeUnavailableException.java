package pulsoescolar_api.exception.auth;

import pulsoescolar_api.exception.common.BusinessValidationException;

public class VerificationCodeUnavailableException extends BusinessValidationException {
    public VerificationCodeUnavailableException() {
        super("Código expirado ou limite de tentativas atingido. Solicite reenvio.");
    }
}

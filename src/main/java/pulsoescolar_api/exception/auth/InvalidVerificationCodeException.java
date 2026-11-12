package pulsoescolar_api.exception.auth;

import pulsoescolar_api.exception.common.BusinessValidationException;

public class InvalidVerificationCodeException extends BusinessValidationException {
    public InvalidVerificationCodeException() {
        super("Código inválido.");
    }
}

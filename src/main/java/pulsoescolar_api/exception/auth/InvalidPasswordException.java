package pulsoescolar_api.exception.auth;

import pulsoescolar_api.exception.common.BusinessValidationException;

public class InvalidPasswordException extends BusinessValidationException {
    public InvalidPasswordException() {
        super("A senha deve ter pelo menos 8 caracteres, uma letra maiúscula e um número.");
    }
}

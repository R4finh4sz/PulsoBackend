package pulsoescolar_api.exception.auth;

import pulsoescolar_api.exception.common.BusinessValidationException;

public class PasswordTooLongException extends BusinessValidationException {
    public PasswordTooLongException() {
        super("A senha é muito longa. Use uma senha mais curta.");
    }
}

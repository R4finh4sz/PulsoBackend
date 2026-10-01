package pulsoescolar_api.exception;

public class PasswordTooLongException extends BusinessValidationException {
    public PasswordTooLongException() {
        super("A senha é muito longa. Use uma senha mais curta.");
    }
}

package pulsoescolar_api.exception;

public class InvalidPasswordException extends BusinessValidationException {
    public InvalidPasswordException() {
        super("A senha deve ter pelo menos 8 caracteres, uma letra maiúscula e um número.");
    }
}

package pulsoescolar_api.exception;

public class SessionUnavailableException extends RuntimeException {
    public SessionUnavailableException() {
        super("Sessão inválida ou expirada.");
    }
}

package pulsoescolar_api.exception.auth;

public class SessionUnavailableException extends RuntimeException {
    public SessionUnavailableException() {
        super("Sessão inválida ou expirada.");
    }
}

package pulsoescolar_api.exception.auth;

public class LoginLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginLockedException(long retryAfterSeconds) {
        super("Muitas tentativas de login. Tente novamente em alguns minutos.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}

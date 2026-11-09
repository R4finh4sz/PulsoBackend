package pulsoescolar_api.exception.auth;

import org.springframework.security.authentication.BadCredentialsException;

public class InvalidRefreshTokenException extends BadCredentialsException {
    public InvalidRefreshTokenException() { super("Refresh token inválido ou expirado."); }
}

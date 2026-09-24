package pulsoescolar_api.security.auth;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
public class SessionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID sessionId;
        Long userId;
        try {
            sessionId = UUID.fromString(jwt.getId());
            userId = Long.valueOf(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Token inválido.");
        }
        if (!sessions.isActive(sessionId, userId, clock.instant())) {
            throw new InvalidBearerTokenException("Sessão expirada ou encerrada.");
        }
        var user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Sessão inválida."));
        var session = sessions.findById(sessionId).orElseThrow(() -> new InvalidBearerTokenException("Sessão inválida."));
        if (!session.isTwoFactorVerified()) {
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("TWO_FACTOR_PENDING")), user.getEmail());
        }
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(user.isFirstLogin()
                        ? "PASSWORD_CHANGE_REQUIRED" : "ROLE_" + user.getRole().name())), user.getEmail());
    }
}

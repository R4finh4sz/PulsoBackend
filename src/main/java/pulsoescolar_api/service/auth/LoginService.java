package pulsoescolar_api.service.auth;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.config.JwtConfig;
import pulsoescolar_api.config.JwtProperties;
import pulsoescolar_api.dto.auth.LoginRequest;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final TwoFactorService twoFactor;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().strip().toLowerCase(Locale.ROOT), request.password()));
        var user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        entityManager.lock(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user.getTwoFactorResendAvailableAt() != null && now.isBefore(user.getTwoFactorResendAvailableAt())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Aguarde 3 minutos para solicitar outro código.");
        }
        var expiresAt = now.plus(properties.sessionTtl()).truncatedTo(ChronoUnit.SECONDS);
        var sessionId = UUID.randomUUID();
        var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE)).subject(user.getId().toString())
                .id(sessionId.toString()).issuedAt(now).expiresAt(expiresAt).build();
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        sessions.deleteExpired(now);
        var session = new AuthSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);
        twoFactor.issue(session);
        sessions.save(session);
        var loginUser = new LoginResponse.LoginUser(user.getRole(),
                user.getClassroom() == null ? null : user.getClassroom().getId(),
                user.getSchool() == null ? null : user.getSchool().getId(), user.isFirstLogin(), user.isTermsAccepted());
        return new LoginResponse(token.getTokenValue(), "Bearer", expiresAt, loginUser,
                true, session.getCodeExpiresAt(), session.getResendAvailableAt());
    }

    @Transactional
    public void logout(Jwt jwt) {
        sessions.revoke(UUID.fromString(jwt.getId()));
    }
}

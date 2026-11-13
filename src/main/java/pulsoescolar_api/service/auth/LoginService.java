package pulsoescolar_api.service.auth;

import pulsoescolar_api.exception.auth.VerificationCooldownException;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.entity.auth.RefreshToken;
import pulsoescolar_api.repository.auth.RefreshTokenRepository;
import pulsoescolar_api.exception.auth.InvalidRefreshTokenException;
import pulsoescolar_api.service.audit.AuthAuditService;
import static pulsoescolar_api.service.audit.AuthAuditEvent.*;

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
    private final RefreshTokenRepository refreshTokens;
    private final AuthAuditService audit;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().strip().toLowerCase(Locale.ROOT), request.password()));
        var user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user.getDeletedAt() != null) throw new BadCredentialsException("Conta excluída.");
        if (user.isTwoFactorEnabled() && user.getTwoFactorResendAvailableAt() != null && now.isBefore(user.getTwoFactorResendAvailableAt())) {
            throw new VerificationCooldownException("Aguarde 3 minutos para solicitar outro código.");
        }
        var expiresAt = now.plus(properties.sessionTtl()).plus(properties.refreshTtl()).truncatedTo(ChronoUnit.SECONDS);
        var sessionId = UUID.randomUUID();
        sessions.deleteExpired(now);
        var session = new AuthSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(expiresAt);
        if (user.isTwoFactorEnabled()) {
            twoFactor.issue(session);
        } else {
            sessions.revokeAll(user.getId());
            session.setTwoFactorVerified(true);
        }
        sessions.save(session);
        var result = response(session, issueRefreshToken(session), now);
        audit.record(session.isTwoFactorVerified() ? LOGIN_SUCCEEDED : LOGIN_2FA_PENDING, user.getId());
        return result;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponse refresh(String rawToken) {
        var hash = hash(rawToken);
        var found = refreshTokens.findById(hash).orElseThrow(InvalidRefreshTokenException::new);
        var user = found.getSession().getUser();
        // Todas as mudanças de autenticação travam primeiro o usuário, depois a sessão.
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var session = sessions.lockById(found.getSession().getId()).orElseThrow(InvalidRefreshTokenException::new);
        entityManager.refresh(session);
        entityManager.refresh(found);
        if (found.getUsedAt() != null) {
            // Reutilização do refresh encerra a sessão, inclusive o acesso adicional.
            sessions.revoke(session.getId());
            audit.record(REFRESH_TOKEN_REUSED, user.getId());
            throw new InvalidRefreshTokenException();
        }
        var now = clock.instant();
        if (user.getDeletedAt() != null || !session.isTwoFactorVerified() || !session.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        found.setUsedAt(now);
        var extendedUntil = now.truncatedTo(ChronoUnit.SECONDS).plus(properties.refreshTtl());
        if (extendedUntil.isBefore(session.getExpiresAt())) session.setExpiresAt(extendedUntil);
        var result = response(session, null, now);
        audit.record(TOKEN_REFRESHED, user.getId());
        return result;
    }

    private String issueRefreshToken(AuthSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setSession(session);
        refreshTokens.save(token);
        return raw;
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    private LoginResponse response(AuthSession session, String refreshToken, java.time.Instant issuedAt) {
        var user = session.getUser();
        var now = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = now.plus(refreshToken == null ? properties.refreshTtl() : properties.sessionTtl())
                .truncatedTo(ChronoUnit.SECONDS);
        if (expiresAt.isAfter(session.getExpiresAt())) expiresAt = session.getExpiresAt();
        var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE)).subject(user.getId().toString())
                .id(session.getId().toString()).issuedAt(now).expiresAt(expiresAt).build();
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        boolean termsSatisfied = user.getRole() == Role.ADMIN || user.isTermsAccepted();
        var loginUser = new LoginResponse.LoginUser(user.getRole(),
                user.getActiveClassroom() == null ? null : user.getActiveClassroom().getId(),
                user.getSchool() == null ? null : user.getSchool().getId(), termsSatisfied, user.getTermsAcceptedVersions(),
                user.isSchoolUnavailable());
        return new LoginResponse(token.getTokenValue(), "Bearer", expiresAt, loginUser,
                !session.isTwoFactorVerified(), session.getCodeExpiresAt(), session.getResendAvailableAt(),
                refreshToken, refreshToken == null ? null : session.getExpiresAt());
    }

    @Transactional
    public void logout(Jwt jwt) {
        sessions.revoke(UUID.fromString(jwt.getId()));
        audit.record(LOGOUT, Long.valueOf(jwt.getSubject()));
    }
}

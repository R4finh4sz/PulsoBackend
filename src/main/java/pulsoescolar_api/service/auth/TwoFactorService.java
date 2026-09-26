package pulsoescolar_api.service.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pulsoescolar_api.dto.auth.TwoFactorResponse;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.service.mail.TwoFactorMailService;

@Service
@RequiredArgsConstructor
public class TwoFactorService {
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final TwoFactorMailService mail;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    public void issue(AuthSession session) {
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        session.setCodeHash(passwords.encode(code));
        session.setCodeExpiresAt(clock.instant().plusSeconds(600));
        session.setResendAvailableAt(clock.instant().plusSeconds(10)); 
        session.getUser().setTwoFactorResendAvailableAt(session.getResendAvailableAt());
        session.setCodeAttempts(0);
        mail.send(session.getUser().getEmail(), code);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(Jwt jwt, String code) {
        var session = pending(jwt);
        if (session.getCodeAttempts() >= 5 || !session.getCodeExpiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código expirado ou limite de tentativas atingido. Solicite reenvio.");
        }
        session.setCodeAttempts(session.getCodeAttempts() + 1);
        if (!passwords.matches(code, session.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido.");
        }
        session.setTwoFactorVerified(true);
        session.setCodeHash(null);
        session.getUser().setTwoFactorResendAvailableAt(null);
    }

    @Transactional
    public TwoFactorResponse resend(Jwt jwt) {
        var session = pending(jwt);
        var nextSend = session.getUser().getTwoFactorResendAvailableAt();
        if (clock.instant().isBefore(session.getResendAvailableAt()) || (nextSend != null && clock.instant().isBefore(nextSend))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde 3 minutos entre os envios.");
        }
        issue(session);
        return new TwoFactorResponse(true, session.getCodeExpiresAt(), session.getResendAvailableAt());
    }

    private AuthSession pending(Jwt jwt) {
        entityManager.find(pulsoescolar_api.entity.user.SchoolUser.class, Long.valueOf(jwt.getSubject()),
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var session = sessions.lockById(UUID.fromString(jwt.getId())).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!session.getUser().getId().toString().equals(jwt.getSubject()) || !session.getExpiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (session.isTwoFactorVerified()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Código já confirmado.");
        return session;
    }
}

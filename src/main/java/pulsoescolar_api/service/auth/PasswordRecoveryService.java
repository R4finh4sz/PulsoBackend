package pulsoescolar_api.service.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.auth.*;
import pulsoescolar_api.entity.auth.PasswordRecovery;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.exception.BusinessValidationException;
import pulsoescolar_api.exception.InvalidVerificationCodeException;
import pulsoescolar_api.exception.VerificationCodeUnavailableException;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.auth.PasswordRecoveryRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.user.PasswordPolicy;
import pulsoescolar_api.service.mail.AccountMailMessages;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordRecoveryRepository recoveries;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void request(String email) {
        var user = lockActiveUser(email);
        // A resposta pública é a mesma para contas existentes, ausentes e em cooldown.
        if (user == null) return;
        var recovery = recoveries.findById(user.getId()).orElseGet(PasswordRecovery::new);
        var now = clock.instant();
        if (recovery.getResendAvailableAt() != null && now.isBefore(recovery.getResendAvailableAt())) return;
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        recovery.setUserId(user.getId());
        recovery.setCodeHash(passwords.encode(code));
        recovery.setCodeExpiresAt(now.plusSeconds(600));
        recovery.setResendAvailableAt(now.plusSeconds(180));
        recovery.setCodeAttempts(0);
        recovery.setResetTokenHash(null);
        recovery.setResetExpiresAt(null);
        recoveries.save(recovery);
        events.publishEvent(AccountMailMessages.recoveryCode(user.getEmail(), code));
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public RecoveryVerificationResponse verify(VerifyRecoveryCodeRequest input) {
        var user = lockActiveUser(input.email());
        if (user == null) throw new VerificationCodeUnavailableException();
        var recovery = recoveries.findById(user.getId()).orElseThrow(VerificationCodeUnavailableException::new);
        if (recovery.getCodeHash() == null || recovery.getCodeAttempts() >= 5
                || !recovery.getCodeExpiresAt().isAfter(clock.instant())) {
            throw new VerificationCodeUnavailableException();
        }
        recovery.setCodeAttempts(recovery.getCodeAttempts() + 1);
        if (!passwords.matches(input.code(), recovery.getCodeHash())) throw new InvalidVerificationCodeException();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        recovery.setCodeHash(null);
        recovery.setResetTokenHash(passwords.encode(token));
        recovery.setResetExpiresAt(clock.instant().plusSeconds(600));
        return new RecoveryVerificationResponse(token, recovery.getResetExpiresAt());
    }

    @Transactional
    public void reset(ResetPasswordRequest input) {
        var user = lockActiveUser(input.email());
        if (user == null) throw invalidReset();
        var recovery = recoveries.findById(user.getId()).orElseThrow(this::invalidReset);
        if (recovery.getResetTokenHash() == null || recovery.getResetExpiresAt() == null
                || !recovery.getResetExpiresAt().isAfter(clock.instant())
                || !passwords.matches(input.resetToken(), recovery.getResetTokenHash())) throw invalidReset();
        if (!input.newPassword().equals(input.confirmPassword())) {
            throw new BusinessValidationException("A confirmação de senha deve ser igual à nova senha.");
        }
        policy.requireValidNewPassword(input.newPassword());
        if (passwords.matches(input.newPassword(), user.getPasswordHash())) {
            throw new BusinessValidationException("A nova senha deve ser diferente da atual.");
        }
        user.setPasswordHash(passwords.encode(input.newPassword()));
        user.setTwoFactorResendAvailableAt(null);
        recovery.setResetTokenHash(null);
        recovery.setResetExpiresAt(null);
        sessions.revokeAll(user.getId());
        events.publishEvent(AccountMailMessages.passwordReset(user.getEmail()));
    }

    private SchoolUser lockActiveUser(String email) {
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) return null;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user.getDeletedAt() == null ? user : null;
    }

    private BusinessValidationException invalidReset() {
        return new BusinessValidationException("A autorização para redefinir a senha é inválida ou expirou. Solicite um novo código.");
    }
}

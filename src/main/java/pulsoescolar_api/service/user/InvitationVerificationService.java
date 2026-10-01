package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.InvalidVerificationCodeException;
import pulsoescolar_api.exception.VerificationAlreadyCompletedException;
import pulsoescolar_api.exception.VerificationCodeUnavailableException;
import pulsoescolar_api.exception.VerificationCooldownException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pulsoescolar_api.entity.user.UserInvitation;
import pulsoescolar_api.service.mail.InvitationMailService;

@Service
@RequiredArgsConstructor
public class InvitationVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(3);
    private final PasswordEncoder passwords;
    private final InvitationMailService mail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public void issueInitialCode(UserInvitation invitation) {
        if (invitation.getVerifiedAt() == null && invitation.getVerificationCodeExpiresAt() == null) {
            issueCode(invitation);
        }
    }

    public void resend(UserInvitation invitation) {
        requirePending(invitation);
        if (invitation.getResendAvailableAt() != null
                && clock.instant().isBefore(invitation.getResendAvailableAt())) {
            throw new VerificationCooldownException("Aguarde 3 minutos entre os envios.");
        }
        issueCode(invitation);
    }

    public void verify(UserInvitation invitation, String code) {
        requirePending(invitation);
        if (invitation.getVerificationAttempts() >= 5 || invitation.getVerificationCodeExpiresAt() == null
                || !invitation.getVerificationCodeExpiresAt().isAfter(clock.instant())) {
            throw new VerificationCodeUnavailableException();
        }
        invitation.setVerificationAttempts(invitation.getVerificationAttempts() + 1);
        if (!passwords.matches(code, invitation.getVerificationCodeHash())) {
            throw new InvalidVerificationCodeException();
        }
        invitation.setVerifiedAt(clock.instant());
        invitation.setVerificationCodeHash(null);
    }

    private void requirePending(UserInvitation invitation) {
        if (invitation.getVerifiedAt() != null) {
            throw new VerificationAlreadyCompletedException();
        }
    }

    private void issueCode(UserInvitation invitation) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var now = clock.instant();
        invitation.setVerificationCodeHash(passwords.encode(code));
        invitation.setVerificationCodeExpiresAt(now.plus(CODE_TTL));
        invitation.setResendAvailableAt(now.plus(RESEND_INTERVAL));
        invitation.setVerificationAttempts(0);
        mail.sendCode(invitation.getEmail(), code);
    }
}

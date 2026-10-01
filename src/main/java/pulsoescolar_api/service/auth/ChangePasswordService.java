package pulsoescolar_api.service.auth;

import pulsoescolar_api.exception.BusinessValidationException;
import pulsoescolar_api.exception.SessionUnavailableException;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.auth.ChangePasswordRequest;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.user.PasswordPolicy;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthSessionRepository sessions;
    private final PasswordPolicy passwordPolicy;

    @Transactional
    public void change(Jwt jwt, ChangePasswordRequest request) {
        var user = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow(() ->
                new SessionUnavailableException());
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessValidationException("Senha atual incorreta.");
        }
        passwordPolicy.requireValidNewPassword(request.newPassword());
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessValidationException("A nova senha deve ser diferente da atual.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        sessions.revokeOthers(user.getId(), UUID.fromString(jwt.getId()));
    }
}

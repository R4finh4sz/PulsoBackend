package pulsoescolar_api.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pulsoescolar_api.dto.auth.ChangePasswordRequest;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthSessionRepository sessions;
    private final pulsoescolar_api.repository.terms.TermsRepository terms;

    @Transactional
    public void change(Jwt jwt, ChangePasswordRequest request) {
        terms.lockCurrent();
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "É necessário aceitar os termos.");
        }
        var user = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ter no máximo 72 bytes.");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ser diferente da atual.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setFirstLogin(false);
        user.setTermsAccepted(true);
        sessions.revokeOthers(user.getId(), UUID.fromString(jwt.getId()));
    }
}

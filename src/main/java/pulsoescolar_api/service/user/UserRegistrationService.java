package pulsoescolar_api.service.user;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.CreateUser;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;
import pulsoescolar_api.service.auth.PasswordGenerator;
import pulsoescolar_api.service.mail.WelcomeMailService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRegistrationService {
    private final PasswordGenerator passwords;
    private final WelcomeMailService mail;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final UserMapper mapper;

    @Transactional
    public UserResponse createUser(CreateUser request, Role role) {
        accessPolicy.requireCreation(currentUser.get().getRole(), role);
        var user = new SchoolUser();
        user.setFullName(request.fullName().strip());
        user.setRa(request.ra().strip());
        user.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        String password = passwords.generate();
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        users.saveAndFlush(user);
        mail.send(user.getEmail(), user.getFullName(), password);
        return mapper.toResponse(user);
    }
}

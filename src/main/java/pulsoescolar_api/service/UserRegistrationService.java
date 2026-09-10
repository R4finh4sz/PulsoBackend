package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.CreateUser;
import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.entity.Role;
import pulsoescolar_api.entity.SchoolUser;
import pulsoescolar_api.mapper.UserMapper;
import pulsoescolar_api.repository.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRegistrationService {
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
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(role);
        return mapper.toResponse(users.saveAndFlush(user));
    }
}

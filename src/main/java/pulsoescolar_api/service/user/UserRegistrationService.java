package pulsoescolar_api.service.user;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.school.SchoolRepository;

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
    private final SchoolRepository schools;

    @Transactional
    public UserResponse createUser(CreateUser request, Role role) {
        var actor = currentUser.get();
        accessPolicy.requireCreation(actor.getRole(), role);
        var user = new SchoolUser();
        if (actor.getRole() == Role.ADMIN) {
            if (request.schoolId() == null || request.schoolId() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "schoolId é obrigatório para cadastros feitos pelo administrador.");
            }
            user.setSchool(schools.findById(request.schoolId()).orElseThrow(() ->
                    new ResourceNotFoundException("Escola não encontrada.")));
        } else {
            if (actor.getSchool() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O coordenador precisa estar vinculado a uma escola para cadastrar usuários.");
            }
            if (request.schoolId() != null && !request.schoolId().equals(actor.getSchool().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O cadastro deve usar a escola do coordenador.");
            }
            user.setSchool(actor.getSchool());
        }
        user.setFullName(request.fullName().strip());
        user.setRa(request.ra().strip());
        user.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        String password = passwords.generate();
        user.setPasswordHash(encoder.encode(password));
        user.setFirstLogin(true);
        user.setRole(role);
        users.saveAndFlush(user);
        mail.send(user.getEmail(), user.getFullName(), password);
        return mapper.toResponse(user);
    }
}

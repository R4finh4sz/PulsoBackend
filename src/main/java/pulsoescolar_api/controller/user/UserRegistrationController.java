package pulsoescolar_api.controller.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.user.CreateUser;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.service.user.UserRegistrationService;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserRegistrationController {
    private final UserRegistrationService service;

    @PostMapping("/students")
    @ResponseStatus(CREATED)
    public UserResponse student(@Valid @RequestBody CreateUser request) {
        return service.createUser(request, Role.STUDENT);
    }

    @PostMapping("/teachers")
    @ResponseStatus(CREATED)
    public UserResponse teacher(@Valid @RequestBody CreateUser request) {
        return service.createUser(request, Role.TEACHER);
    }

    @PostMapping("/coordinators")
    @ResponseStatus(CREATED)
    public UserResponse coordinator(@Valid @RequestBody CreateUser request) {
        return service.createUser(request, Role.PEDAGOGICAL_COORDINATOR);
    }
}

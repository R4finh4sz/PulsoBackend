package pulsoescolar_api.service.user.student;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pulsoescolar_api.dto.user.SelfRegistrationRequest;
import pulsoescolar_api.dto.user.RegistrationReceipt;
import pulsoescolar_api.dto.user.UserPageResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.service.user.UserManagementService;
import pulsoescolar_api.service.user.SelfRegistrationService;
import pulsoescolar_api.dto.user.UpdateUserRequest;
import pulsoescolar_api.dto.user.UserResponse;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final UserManagementService users;
    private final SelfRegistrationService registration;

    public RegistrationReceipt create(SelfRegistrationRequest request) {
        return registration.register(request, Role.STUDENT, null);
    }

    public UserPageResponse list(String q, Long classroomId, Boolean unassigned, int page, int size) {
        return users.list(Role.STUDENT, q, classroomId, unassigned, page, size);
    }

    public UserResponse get(Long id) {
        return users.get(id, Role.STUDENT);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        return users.update(id, Role.STUDENT, request);
    }
}

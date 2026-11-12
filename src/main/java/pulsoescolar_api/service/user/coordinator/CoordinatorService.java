package pulsoescolar_api.service.user.coordinator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pulsoescolar_api.dto.user.SelfRegistrationRequest;
import pulsoescolar_api.dto.user.RegistrationReceipt;
import pulsoescolar_api.dto.user.UserPageResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.service.user.UserManagementService;
import pulsoescolar_api.service.user.SelfRegistrationService;

@Service
@RequiredArgsConstructor
public class CoordinatorService {
    private final UserManagementService users;
    private final SelfRegistrationService registration;

    public RegistrationReceipt create(SelfRegistrationRequest request) {
        return registration.register(request, Role.PEDAGOGICAL_COORDINATOR, null);
    }

    public UserPageResponse list(String q, int page, int size) {
        return users.list(Role.PEDAGOGICAL_COORDINATOR, q, null, null, page, size);
    }

}

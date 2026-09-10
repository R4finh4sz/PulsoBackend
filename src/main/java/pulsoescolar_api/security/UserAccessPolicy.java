package pulsoescolar_api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.Role;

@Component
public class UserAccessPolicy {
    public void requireManager(Role actorRole) {
        if (actorRole != Role.ADMIN && actorRole != Role.PEDAGOGICAL_COORDINATOR) {
            throw new AccessDeniedException("Acesso de Coordenador necessario");
        }
    }

    public void requireCreation(Role actorRole, Role requestedRole) {
        requireManager(actorRole);
        boolean allowed = requestedRole == Role.STUDENT || requestedRole == Role.TEACHER
                || (requestedRole == Role.PEDAGOGICAL_COORDINATOR && actorRole == Role.ADMIN);
        if (!allowed) {
            throw new AccessDeniedException("Cannot create this role");
        }
    }
}

package pulsoescolar_api.security.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.user.*;

@Component
public class RegistrationAccessPolicy {
    public void requireReviewer(SchoolUser actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.PEDAGOGICAL_COORDINATOR && actor.getSchool() != null) return;
        throw new AccessDeniedException("Você não pode analisar solicitações de cadastro.");
    }

    public void requireAccess(SchoolUser actor, RegistrationRequest request) {
        requireReviewer(actor);
        boolean allowed = actor.getRole() == Role.ADMIN
                ? request.getRole() == Role.PEDAGOGICAL_COORDINATOR
                : (request.getRole() == Role.STUDENT || request.getRole() == Role.TEACHER)
                    && actor.getSchool().getId().equals(request.getSchool().getId());
        if (!allowed) throw new AccessDeniedException("Esta solicitação não pertence à sua fila de aprovação.");
    }
}

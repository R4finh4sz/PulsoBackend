package pulsoescolar_api.security.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;

@Component
public class InvitationAccessPolicy {
    public void requireCoordinatorInvitation(SchoolUser actor) {
        requireRole(actor, Role.ADMIN);
    }

    public void requireTeacherInvitation(SchoolUser actor) {
        requireRole(actor, Role.PEDAGOGICAL_COORDINATOR);
        if (actor.getSchool() == null) {
            throw new AccessDeniedException("Coordenador sem instituição.");
        }
    }

    private void requireRole(SchoolUser actor, Role role) {
        if (actor.getRole() != role || actor.getDeletedAt() != null) {
            throw new AccessDeniedException("Você não pode enviar este convite.");
        }
    }
}

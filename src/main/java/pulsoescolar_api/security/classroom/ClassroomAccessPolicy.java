package pulsoescolar_api.security.classroom;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;

@Component
public class ClassroomAccessPolicy {
    public void requireAccess(Classroom classroom, SchoolUser user) {
        requireActive(classroom);
        boolean allowed = switch (user.getRole()) {
            case ADMIN -> false;
            case PEDAGOGICAL_COORDINATOR -> user.getSchool() != null && user.isInActiveSchool(classroom);
            case TEACHER -> user.isInActiveSchool(classroom) && isAssigned(classroom, user);
            case STUDENT -> user.isInActiveSchool(classroom) && user.getClassroom() != null
                    && user.getClassroom().getId().equals(classroom.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("Você não tem permissão para acessar esta sala.");
        }
    }

    public void requireRosterAccess(Classroom classroom, SchoolUser user) {
        requireAccess(classroom, user);
        if (user.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("A consulta de participantes exige coordenador ou professor vinculado à sala.");
        }
    }

    public void requireAssignedTeacher(Classroom classroom, SchoolUser user) {
        requireActive(classroom);
        if (user.getRole() != Role.TEACHER || !user.isInActiveSchool(classroom) || !isAssigned(classroom, user)) {
            throw new AccessDeniedException("Esta operação exige um professor vinculado à sala.");
        }
    }

    private boolean isAssigned(Classroom classroom, SchoolUser user) {
        return classroom.getTeachers().stream().anyMatch(teacher -> teacher.getId().equals(user.getId()));
    }

    private void requireActive(Classroom classroom) {
        if (classroom.getSchool() != null) classroom.getSchool().requireActive();
        if (classroom.getDeletedAt() != null) {
            throw new pulsoescolar_api.exception.common.ResourceNotFoundException("Sala não encontrada.");
        }
    }
}

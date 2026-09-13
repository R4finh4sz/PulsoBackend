package pulsoescolar_api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.Classroom;
import pulsoescolar_api.entity.Role;
import pulsoescolar_api.entity.SchoolUser;

@Component
public class ClassroomAccessPolicy {
    public void requireAccess(Classroom classroom, SchoolUser user) {
        boolean allowed = switch (user.getRole()) {
            case ADMIN, PEDAGOGICAL_COORDINATOR -> true;
            case TEACHER -> isAssigned(classroom, user);
            case STUDENT -> user.getClassroom() != null
                    && user.getClassroom().getId().equals(classroom.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("Acesso a sala bloqueado");
        }
    }

    public void requireRosterAccess(Classroom classroom, SchoolUser user) {
        requireAccess(classroom, user);
        if (user.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Necessario ser um Staff do sistema");
        }
    }

    public void requireAssignedTeacher(Classroom classroom, SchoolUser user) {
        if (user.getRole() != Role.TEACHER || !isAssigned(classroom, user)) {
            throw new AccessDeniedException("Professor necessário");
        }
    }

    private boolean isAssigned(Classroom classroom, SchoolUser user) {
        return classroom.getTeachers().stream().anyMatch(teacher -> teacher.getId().equals(user.getId()));
    }
}

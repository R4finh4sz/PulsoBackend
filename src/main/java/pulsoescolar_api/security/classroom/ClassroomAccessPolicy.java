package pulsoescolar_api.security.classroom;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;

@Component
public class ClassroomAccessPolicy {
    public void requireAccess(Classroom classroom, SchoolUser user) {
        boolean allowed = switch (user.getRole()) {
            case ADMIN -> true;
            case PEDAGOGICAL_COORDINATOR -> user.getSchool() == null || classroom.getSchool() == null
                    || user.getSchool().getId().equals(classroom.getSchool().getId());
            case TEACHER -> isAssigned(classroom, user);
            case STUDENT -> user.getClassroom() != null
                    && user.getClassroom().getId().equals(classroom.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("Você não tem permissão para acessar esta sala.");
        }
    }

    public void requireRosterAccess(Classroom classroom, SchoolUser user) {
        requireAccess(classroom, user);
        if (user.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("A consulta de alunos exige acesso de administrador, coordenador pedagógico ou professor vinculado à sala.");
        }
    }

    public void requireAssignedTeacher(Classroom classroom, SchoolUser user) {
        if (user.getRole() != Role.TEACHER || !isAssigned(classroom, user)) {
            throw new AccessDeniedException("Esta operação exige um professor vinculado à sala.");
        }
    }

    private boolean isAssigned(Classroom classroom, SchoolUser user) {
        return classroom.getTeachers().stream().anyMatch(teacher -> teacher.getId().equals(user.getId()));
    }
}

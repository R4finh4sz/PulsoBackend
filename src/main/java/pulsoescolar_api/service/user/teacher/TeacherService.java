package pulsoescolar_api.service.user.teacher;

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
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final UserManagementService users;
    private final SelfRegistrationService registration;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final UserRepository userRepository;
    private final SubjectRepository subjects;
    private final ClassroomRepository classrooms;

    public RegistrationReceipt create(SelfRegistrationRequest request) {
        return registration.register(request, Role.TEACHER, null);
    }

    public UserPageResponse list(String q, int page, int size) {
        return users.list(Role.TEACHER, q, null, null, null, page, size);
    }

    public UserResponse get(Long id) {
        return users.get(id, Role.TEACHER);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        return users.update(id, Role.TEACHER, request);
    }

    /** Coordenador desvincula um professor da própria escola. */
    @Transactional
    public void removeFromSchool(Long schoolId, Long teacherId) {
        var actor = currentUser.get();
        accessPolicy.requireCoordinator(actor);
        if (!actor.getSchool().getId().equals(schoolId)) {
            throw new org.springframework.security.access.AccessDeniedException("O coordenador só gerencia a própria escola.");
        }
        var teacher = userRepository.lockById(teacherId)
                .filter(user -> user.getRole() == Role.TEACHER && user.getDeletedAt() == null
                        && user.getSchools().stream().anyMatch(school -> school.getId().equals(schoolId)))
                .orElseThrow(() -> new ResourceNotFoundException("Professor não encontrado nesta escola."));
        if (subjects.hasTeacherInSchool(schoolId, teacherId)) {
            throw new BusinessConflictException("O professor ainda é responsável por disciplinas desta escola. Retire-o das disciplinas antes.");
        }
        classrooms.findByTeachersId(teacherId).stream()
                .filter(room -> room.getSchool() != null && room.getSchool().getId().equals(schoolId))
                .forEach(room -> room.getTeachers().removeIf(t -> t.getId().equals(teacherId)));
        teacher.getSchools().removeIf(school -> school.getId().equals(schoolId));
        boolean lostCurrent = teacher.getEnrollmentSchool() != null && teacher.getEnrollmentSchool().getId().equals(schoolId)
                || teacher.getActiveSchool() != null && teacher.getActiveSchool().getId().equals(schoolId);
        if (lostCurrent) teacher.setSchool(teacher.getSchools().stream().findFirst().orElse(null));
    }
}

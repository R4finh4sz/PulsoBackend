package pulsoescolar_api.service.user.student;

import pulsoescolar_api.exception.common.ResourceNotFoundException;

import pulsoescolar_api.service.classroom.ClassroomLookupService;
import pulsoescolar_api.service.user.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentEnrollmentService {
    private final ClassroomLookupService classrooms;
    private final UserLookupService users;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final UserMapper mapper;
    private final pulsoescolar_api.security.classroom.ClassroomAccessPolicy classroomPolicy;
    private final pulsoescolar_api.service.activity.ActivityRecipientService recipients;

    @Transactional
    public void unenroll(Long classroomId, Long studentId) {
        accessPolicy.requireCoordinator(currentUser.get());
        classroomPolicy.requireAccess(classrooms.findById(classroomId), currentUser.get());
        var student = users.findByRole(studentId, Role.STUDENT);
        if (student.getClassroom() == null) return;
        if (!student.getClassroom().getId().equals(classroomId)) {
            throw new ResourceNotFoundException("Aluno não vinculado a esta sala.");
        }
        student.setClassroom(null);
    }

    @Transactional
    public UserResponse enroll(Long classroomId, Long studentId) {
        accessPolicy.requireCoordinator(currentUser.get());
        var classroom = classrooms.findById(classroomId);
        classroomPolicy.requireAccess(classroom, currentUser.get());
        var student = users.findByRole(studentId, Role.STUDENT);
        if (classroom.getSchool() != null && !student.belongsToSchool(classroom.getSchool().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("O aluno deve pertencer à escola da turma.");
        }
        student.setClassroom(classroom);
        recipients.assignOpenActivities(student);
        return mapper.toResponse(student);
    }
}

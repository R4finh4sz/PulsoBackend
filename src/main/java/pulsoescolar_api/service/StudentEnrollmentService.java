package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.entity.Role;
import pulsoescolar_api.mapper.UserMapper;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentEnrollmentService {
    private final ClassroomLookupService classrooms;
    private final UserLookupService users;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final UserMapper mapper;

    @Transactional
    public UserResponse enroll(Long classroomId, Long studentId) {
        accessPolicy.requireManager(currentUser.get().getRole());
        var classroom = classrooms.findById(classroomId);
        var student = users.findByRole(studentId, Role.STUDENT);
        student.setClassroom(classroom);
        return mapper.toResponse(student);
    }
}

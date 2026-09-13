package pulsoescolar_api.service.teacher;

import pulsoescolar_api.service.classroom.ClassroomLookupService;
import pulsoescolar_api.service.user.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.mapper.classroom.ClassroomMapper;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherAssignmentService {
    private final ClassroomLookupService classrooms;
    private final UserLookupService users;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final ClassroomMapper mapper;

    @Transactional
    public ClassroomResponse assign(Long classroomId, Long teacherId) {
        accessPolicy.requireManager(currentUser.get().getRole());
        var classroom = classrooms.findById(classroomId);
        classroom.getTeachers().add(users.findByRole(teacherId, Role.TEACHER));
        return mapper.toResponse(classroom);
    }
}

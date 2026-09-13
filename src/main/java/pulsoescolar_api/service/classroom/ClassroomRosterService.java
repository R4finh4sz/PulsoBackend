package pulsoescolar_api.service.classroom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.mapper.user.UserMapper;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomRosterService {
    private final ClassroomLookupService classrooms;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final ClassroomAccessPolicy accessPolicy;
    private final UserMapper mapper;

    public List<UserResponse> students(Long classroomId) {
        var classroom = classrooms.findById(classroomId);
        accessPolicy.requireRosterAccess(classroom, currentUser.get());
        return users.findByClassroomId(classroomId).stream().map(mapper::toResponse).toList();
    }
}

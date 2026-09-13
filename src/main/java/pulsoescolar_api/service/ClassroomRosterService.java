package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.mapper.UserMapper;
import pulsoescolar_api.repository.UserRepository;
import pulsoescolar_api.security.ClassroomAccessPolicy;
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

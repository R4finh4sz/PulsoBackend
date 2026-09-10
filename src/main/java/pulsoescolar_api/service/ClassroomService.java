package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.ClassroomResponse;
import pulsoescolar_api.dto.CreateClassroomRequest;
import pulsoescolar_api.entity.Classroom;
import pulsoescolar_api.mapper.ClassroomMapper;
import pulsoescolar_api.repository.ClassroomRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomService {
    private final ClassroomRepository classrooms;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final ClassroomMapper mapper;

    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request) {
        accessPolicy.requireManager(currentUser.get().getRole());
        var classroom = new Classroom();
        classroom.setName(request.name().strip());
        classroom.setIdentifier(request.identifier());
        return mapper.toResponse(classrooms.saveAndFlush(classroom));
    }

    public List<ClassroomResponse> listClassrooms() {
        var user = currentUser.get();
        var result = switch (user.getRole()) {
            case ADMIN, PEDAGOGICAL_COORDINATOR -> classrooms.findAll();
            case TEACHER -> classrooms.findByTeachersId(user.getId());
            case STUDENT -> user.getClassroom() == null
                    ? List.<Classroom>of() : List.of(user.getClassroom());
        };
        return result.stream().map(mapper::toResponse).toList();
    }
}

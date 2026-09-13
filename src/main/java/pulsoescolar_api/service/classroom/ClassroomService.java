package pulsoescolar_api.service.classroom;

import pulsoescolar_api.dto.classroom.UpdateClassroomRequest;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import pulsoescolar_api.mapper.user.UserMapper;
import java.util.Comparator;
import pulsoescolar_api.entity.user.SchoolUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.classroom.CreateClassroomRequest;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.mapper.classroom.ClassroomMapper;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomService {
    private final ClassroomLookupService lookup;
    private final ClassroomAccessPolicy classroomPolicy;
    private final UserMapper userMapper;
    private final ClassroomRepository classrooms;
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final ClassroomMapper mapper;

    public ClassroomResponse get(Long id) {
        var classroom = lookup.findById(id);
        classroomPolicy.requireAccess(classroom, currentUser.get());
        return mapper.toResponse(classroom);
    }

    public List<UserResponse> teachers(Long id) {
        var classroom = lookup.findById(id);
        classroomPolicy.requireRosterAccess(classroom, currentUser.get());
        return classroom.getTeachers().stream()
                .sorted(Comparator.comparing(SchoolUser::getFullName)
                        .thenComparing(SchoolUser::getId))
                .map(userMapper::toResponse).toList();
    }

    @Transactional
    public ClassroomResponse update(Long id, UpdateClassroomRequest request) {
        var user = currentUser.get();
        accessPolicy.requireManager(user.getRole());
        var classroom = lookup.findById(id);
        classroomPolicy.requireAccess(classroom, user);
        if (request.name() != null) classroom.setName(request.name().strip());
        if (request.identifier() != null) classroom.setIdentifier(request.identifier());
        return mapper.toResponse(classrooms.saveAndFlush(classroom));
    }

    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request) {
        var user = currentUser.get();
        accessPolicy.requireManager(user.getRole());
        var classroom = new Classroom();
        if (user.getRole() == pulsoescolar_api.entity.user.Role.PEDAGOGICAL_COORDINATOR) {
            classroom.setSchool(user.getSchool());
        }
        classroom.setName(request.name().strip());
        classroom.setIdentifier(request.identifier());
        return mapper.toResponse(classrooms.saveAndFlush(classroom));
    }

    public List<ClassroomResponse> listClassrooms() {
        var user = currentUser.get();
        var result = switch (user.getRole()) {
            case ADMIN -> classrooms.findAll();
            case PEDAGOGICAL_COORDINATOR -> user.getSchool() == null
                    ? List.<Classroom>of() : classrooms.findBySchoolId(user.getSchool().getId());
            case TEACHER -> classrooms.findByTeachersId(user.getId());
            case STUDENT -> user.getClassroom() == null
                    ? List.<Classroom>of() : List.of(user.getClassroom());
        };
        return result.stream().map(mapper::toResponse).toList();
    }
}

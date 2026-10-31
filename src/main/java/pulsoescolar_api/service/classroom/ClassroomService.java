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
    private final java.time.Clock clock;

    @Transactional
    public void delete(Long id) {
        var user = currentUser.get();
        accessPolicy.requireManager(user.getRole());
        var classroom = lookup.findById(id);
        if (user.getRole() != pulsoescolar_api.entity.user.Role.ADMIN
                && (user.getSchool() == null || classroom.getSchool() == null
                || !user.getSchool().getId().equals(classroom.getSchool().getId()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A exclusão exige um coordenador vinculado à escola da turma ou administrador.");
        }
        classroom.setDeletedAt(clock.instant());
    }

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
            case STUDENT -> user.getActiveClassroom() == null
                    ? List.<Classroom>of() : List.of(user.getActiveClassroom());
        };
        return result.stream().filter(classroom -> classroom.getDeletedAt() == null)
                .filter(classroom -> user.getRole() == pulsoescolar_api.entity.user.Role.ADMIN || user.isInActiveSchool(classroom))
                .map(mapper::toResponse).toList();
    }
}

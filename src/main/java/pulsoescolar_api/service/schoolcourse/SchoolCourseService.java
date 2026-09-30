package pulsoescolar_api.service.schoolcourse;

import pulsoescolar_api.service.classroom.ClassroomLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.schoolcourse.NameRequest;
import pulsoescolar_api.dto.schoolcourse.SchoolCourseResponse;
import pulsoescolar_api.entity.schoolcourse.SchoolCourse;
import pulsoescolar_api.mapper.schoolcourse.SchoolCourseMapper;
import pulsoescolar_api.repository.schoolcourse.SchoolCourseRepository;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolCourseService {
    private final SchoolCourseRepository schoolCourses;
    private final ClassroomLookupService classrooms;
    private final CurrentUser currentUser;
    private final ClassroomAccessPolicy accessPolicy;
    private final SchoolCourseMapper mapper;

    @Transactional
    public SchoolCourseResponse createSchoolCourse(Long classroomId, NameRequest request) {
        var classroom = classrooms.findById(classroomId);
        var teacher = currentUser.get();
        accessPolicy.requireAssignedTeacher(classroom, teacher);
        var schoolCourse = new SchoolCourse();
        schoolCourse.setName(request.name().strip());
        schoolCourse.setClassroom(classroom);
        schoolCourse.setTeacher(teacher);
        return mapper.toResponse(schoolCourses.saveAndFlush(schoolCourse));
    }

    public List<SchoolCourseResponse> schoolCourses(Long classroomId) {
        accessPolicy.requireAccess(classrooms.findById(classroomId), currentUser.get());
        return schoolCourses.findByClassroomId(classroomId).stream().map(mapper::toResponse).toList();
    }
}

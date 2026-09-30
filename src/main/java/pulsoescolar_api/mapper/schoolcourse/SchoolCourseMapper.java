package pulsoescolar_api.mapper.schoolcourse;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.schoolcourse.SchoolCourseResponse;
import pulsoescolar_api.entity.schoolcourse.SchoolCourse;

@Component
public class SchoolCourseMapper {
    public SchoolCourseResponse toResponse(SchoolCourse schoolCourse) {
        return new SchoolCourseResponse(schoolCourse.getId(), schoolCourse.getName(),
                schoolCourse.getClassroom().getId(), schoolCourse.getTeacher().getId());
    }
}

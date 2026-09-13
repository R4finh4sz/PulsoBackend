package pulsoescolar_api.mapper.classroom;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.entity.classroom.Classroom;

@Component
public class ClassroomMapper {
    public ClassroomResponse toResponse(Classroom classroom) {
        return new ClassroomResponse(classroom.getId(), classroom.getName(), classroom.getIdentifier(),
                classroom.getTeachers().stream().map(teacher -> teacher.getId()).sorted().toList());
    }
}

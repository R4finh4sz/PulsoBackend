package pulsoescolar_api.mapper;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.ClassroomResponse;
import pulsoescolar_api.entity.Classroom;

@Component
public class ClassroomMapper {
    public ClassroomResponse toResponse(Classroom classroom) {
        return new ClassroomResponse(classroom.getId(), classroom.getName(), classroom.getIdentifier(),
                classroom.getTeachers().stream().map(teacher -> teacher.getId()).sorted().toList());
    }
}

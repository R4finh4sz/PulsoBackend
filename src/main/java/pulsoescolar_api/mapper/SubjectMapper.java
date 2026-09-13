package pulsoescolar_api.mapper;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.SubjectResponse;
import pulsoescolar_api.entity.Subject;

@Component
public class SubjectMapper {
    public SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(),
                subject.getClassroom().getId(), subject.getTeacher().getId());
    }
}

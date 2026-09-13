package pulsoescolar_api.mapper.subject;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.entity.subject.Subject;

@Component
public class SubjectMapper {
    public SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(),
                subject.getClassroom().getId(), subject.getTeacher().getId());
    }
}

package pulsoescolar_api.mapper.subject;

import org.springframework.stereotype.Component;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.dto.subject.CreateSubjectRequest;

@Component
public class SubjectMapper {
    public SubjectResponse toResponse(Subject subject) {
        String syllabus = subject.getTopics().isEmpty() ? subject.getSyllabus()
                : subject.getTopics().stream().map(t -> t.getDescription())
                        .collect(java.util.stream.Collectors.joining("\n\n"));
        return new SubjectResponse(subject.getId(), subject.getName(),
                subject.getClassroom().getId(), subject.getTeacher().getId(),
                syllabus, subject.getTopics().stream()
                        .map(t -> new CreateSubjectRequest.Topic(t.getTitle(), t.getDescription(), t.getReferences())).toList(),
                java.util.stream.Stream.concat(java.util.stream.Stream.of(subject.getTeacher()), subject.getCollaborators().stream())
                        .filter(t -> t.getDeletedAt() == null)
                        .map(t -> t.getId()).distinct().sorted().toList());
    }
}

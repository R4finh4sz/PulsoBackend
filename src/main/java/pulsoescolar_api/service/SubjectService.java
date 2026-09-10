package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.NameRequest;
import pulsoescolar_api.dto.SubjectResponse;
import pulsoescolar_api.entity.Subject;
import pulsoescolar_api.mapper.SubjectMapper;
import pulsoescolar_api.repository.SubjectRepository;
import pulsoescolar_api.security.ClassroomAccessPolicy;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectService {
    private final SubjectRepository subjects;
    private final ClassroomLookupService classrooms;
    private final CurrentUser currentUser;
    private final ClassroomAccessPolicy accessPolicy;
    private final SubjectMapper mapper;

    @Transactional
    public SubjectResponse createSubject(Long classroomId, NameRequest request) {
        var classroom = classrooms.findById(classroomId);
        var teacher = currentUser.get();
        accessPolicy.requireAssignedTeacher(classroom, teacher);
        var subject = new Subject();
        subject.setName(request.name().strip());
        subject.setClassroom(classroom);
        subject.setTeacher(teacher);
        return mapper.toResponse(subjects.saveAndFlush(subject));
    }

    public List<SubjectResponse> subjects(Long classroomId) {
        accessPolicy.requireAccess(classrooms.findById(classroomId), currentUser.get());
        return subjects.findByClassroomId(classroomId).stream().map(mapper::toResponse).toList();
    }
}

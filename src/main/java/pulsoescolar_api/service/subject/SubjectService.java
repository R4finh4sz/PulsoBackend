package pulsoescolar_api.service.subject;

import pulsoescolar_api.exception.common.BusinessValidationException;

import pulsoescolar_api.service.classroom.ClassroomLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.mapper.subject.SubjectMapper;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.dto.subject.CreateSubjectRequest;
import pulsoescolar_api.dto.subject.UpdateSyllabusRequest;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.entity.subject.SubjectTopic;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.entity.user.Role;
import org.springframework.security.access.AccessDeniedException;

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
    public SubjectResponse createSubject(Long classroomId, CreateSubjectRequest request) {
        var subject = newSubject(classroomId, request.name());
        subject.setSyllabus(request.syllabus());
        replaceTopics(subject, request.topics());
        return mapper.toResponse(subjects.saveAndFlush(subject));
    }

    @Transactional
    public SubjectResponse updateSyllabus(Long classroomId, Long subjectId, UpdateSyllabusRequest request) {
        var classroom = classrooms.findById(classroomId);
        var user = currentUser.get();
        accessPolicy.requireAssignedTeacher(classroom, user);
        requireSchool(classroom, user);
        var subject = subjects.findById(subjectId)
                .filter(s -> s.getClassroom().getId().equals(classroomId))
                .orElseThrow(() -> new ResourceNotFoundException("Disciplina não encontrada nesta turma."));
        if (!subject.hasTeacher(user.getId())) {
            throw new AccessDeniedException("Somente professores da disciplina podem editar a ementa.");
        }
        replaceTopics(subject, request.topics());
        return mapper.toResponse(subjects.saveAndFlush(subject));
    }

    private void replaceTopics(Subject subject, List<CreateSubjectRequest.Topic> topics) {
        var titles = new java.util.HashSet<String>();
        subject.getTopics().clear();
        for (var topic : topics) {
            if (!titles.add(topic.title().strip().toLowerCase(java.util.Locale.ROOT))) {
                throw new BusinessValidationException("Os títulos dos tópicos devem ser únicos.");
            }
            subject.getTopics().add(new SubjectTopic(topic.title(), topic.description(), topic.references()));
        }
    }

    private Subject newSubject(Long classroomId, String name) {
        var classroom = classrooms.findById(classroomId);
        var teacher = currentUser.get();
        accessPolicy.requireAssignedTeacher(classroom, teacher);
        requireSchool(classroom, teacher);
        var subject = new Subject();
        subject.setName(name.strip());
        subject.setClassroom(classroom);
        subject.setTeacher(teacher);
        return subject;
    }

    public List<SubjectResponse> subjects(Long classroomId) {
        var classroom = classrooms.findById(classroomId);
        var user = currentUser.get();
        accessPolicy.requireAccess(classroom, user);
        requireSchool(classroom, user);
        return subjects.findByClassroomId(classroomId).stream().map(mapper::toResponse).toList();
    }

    private void requireSchool(Classroom classroom, SchoolUser user) {
        if (user.getRole() == Role.ADMIN) return;
        Long classroomSchool = classroom.getSchool() == null ? null : classroom.getSchool().getId();
        Long userSchool = user.getSchool() == null ? null : user.getSchool().getId();
        if (!java.util.Objects.equals(classroomSchool, userSchool)) {
            throw new AccessDeniedException("A turma deve pertencer à escola do usuário.");
        }
    }
}

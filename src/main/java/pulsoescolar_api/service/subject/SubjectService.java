package pulsoescolar_api.service.subject;

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
import pulsoescolar_api.dto.subject.ThemeRequest;
import pulsoescolar_api.entity.subject.SubjectTopic;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.exception.*;
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
        var titles = new java.util.HashSet<String>();
        var subject = newSubject(classroomId, request.name());
        subject.setSyllabus(request.syllabus());
        for (var topic : request.topics()) {
            if (!titles.add(topic.title().strip().toLowerCase(java.util.Locale.ROOT))) {
                throw new BusinessValidationException("Os títulos dos tópicos devem ser únicos.");
            }
            subject.getTopics().add(new SubjectTopic(topic.title(), topic.description(), topic.references()));
        }
        return mapper.toResponse(subjects.saveAndFlush(subject));
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

    public List<String> themes(Long classroomId, Long subjectId) {
        return sortedThemes(findAccessible(classroomId, subjectId, false));
    }

    @Transactional
    public String addTheme(Long classroomId, Long subjectId, ThemeRequest request) {
        var subject = findAccessible(classroomId, subjectId, true);
        String name = request.name().strip();
        if (subject.getThemes().stream().anyMatch(t -> t.equalsIgnoreCase(name))) {
            throw new BusinessConflictException("Este tema já existe na disciplina.");
        }
        subject.getThemes().add(name);
        return name;
    }

    @Transactional
    public void removeTheme(Long classroomId, Long subjectId, String name) {
        var subject = findAccessible(classroomId, subjectId, true);
        if (!subject.getThemes().removeIf(t -> t.equalsIgnoreCase(name.strip()))) {
            throw new ResourceNotFoundException("Tema não encontrado.");
        }
    }

    private Subject findAccessible(Long classroomId, Long subjectId, boolean writing) {
        var subject = (writing ? subjects.findLockedById(subjectId) : subjects.findById(subjectId))
                .orElseThrow(() -> new ResourceNotFoundException("Disciplina não encontrada."));
        if (!subject.getClassroom().getId().equals(classroomId)) {
            throw new ResourceNotFoundException("Disciplina não encontrada nesta turma.");
        }
        var user = currentUser.get();
        accessPolicy.requireAccess(subject.getClassroom(), user);
        requireSchool(subject.getClassroom(), user);
        if (writing) {
            accessPolicy.requireAssignedTeacher(subject.getClassroom(), user);
            if (!subject.getTeacher().getId().equals(user.getId())) {
                throw new AccessDeniedException("Somente o professor responsável pode alterar os temas.");
            }
        }
        return subject;
    }

    private List<String> sortedThemes(Subject subject) {
        return subject.getThemes().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
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

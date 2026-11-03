package pulsoescolar_api.service.activity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.activity.*;
import pulsoescolar_api.entity.activity.*;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.activity.*;
import pulsoescolar_api.exception.common.*;
import pulsoescolar_api.mapper.activity.ActivityMapper;
import pulsoescolar_api.repository.activity.*;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {
    private final ActivityRepository activities;
    private final QuizSubmissionRepository submissions;
    private final SubjectRepository subjects;
    private final CurrentUser currentUser;
    private final ClassroomAccessPolicy access;
    private final ActivityMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public ActivityResponse create(Long subjectId, CreateActivityRequest input) {
        var subject = subject(subjectId);
        var user = currentUser.get();
        requireTeacher(subject, user);
        var activity = new Activity();
        activity.setSubject(subject);
        activity.setCreatedBy(user);
        activity.setClosesAt(input.closesAt() == null ? null : input.closesAt().truncatedTo(ChronoUnit.MICROS));
        activity.setCreatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        for (var item : input.questions()) {
            if (item.correctOption() >= item.options().size()) {
                throw new BusinessValidationException("O gabarito deve indicar uma alternativa existente.");
            }
            var options = item.options().stream().map(String::strip).toList();
            var unique = new HashSet<String>();
            for (String option : options) {
                if (!unique.add(option.toLowerCase(Locale.forLanguageTag("pt-BR")))) {
                    throw new BusinessValidationException("As quatro alternativas devem ser diferentes.");
                }
            }
            var question = new QuizQuestion();
            question.setText(item.question());
            question.setTheme(subject.getTopics().stream().map(topic -> topic.getTitle()).filter(title -> title.equals(item.theme())).findFirst()
                    .orElseThrow(() -> new BusinessValidationException("Selecione um tópico da ementa desta disciplina.")));
            question.getOptions().addAll(options);
            question.setCorrectOption(item.correctOption());
            activity.getQuestions().add(question);
        }
        return mapper.toResponse(activities.saveAndFlush(activity));
    }

    public List<ActivityResponse> list(Long subjectId, Long classroomId) {
        var subject = subject(subjectId);
        if (classroomId != null && !subject.getClassroom().getId().equals(classroomId)) {
            throw new ResourceNotFoundException("Disciplina não encontrada nesta turma.");
        }
        requireReader(subject, currentUser.get());
        return activities.findBySubjectIdOrderByCreatedAtDescIdDesc(subjectId).stream().map(mapper::toResponse).toList();
    }

    public List<StudentActivityResponse> studentActivities() {
        var user = currentUser.get();
        requireStudent(user);
        var room = user.getActiveClassroom();
        if (room == null) return List.of();
        return activities.findBySubjectClassroomIdOrderByCreatedAtDescIdDesc(room.getId()).stream()
                .map(activity -> new StudentActivityResponse(mapper.toResponse(activity),
                        submissions.findByActivityIdAndStudentId(activity.getId(), user.getId())
                                .map(mapper::toResponse).orElse(null))).toList();
    }

    public QuizResponse open(Long id) {
        var activity = activity(id);
        requireReader(activity.getSubject(), currentUser.get());
        return mapper.toQuiz(activity);
    }

    @Transactional
    public SubmissionResponse submit(Long activityId, SubmitQuizRequest input) {
        var user = currentUser.get();
        // Serialize attempts for this student, including duplicate requests sent simultaneously.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireStudent(user);
        var activity = activity(activityId);
        access.requireAccess(activity.getSubject().getClassroom(), user);
        if (activity.getClosesAt() != null && !clock.instant().isBefore(activity.getClosesAt())) {
            throw new QuizClosedException();
        }
        if (submissions.existsByActivityIdAndStudentId(activityId, user.getId())) {
            throw new ActivityAlreadySubmittedException();
        }
        if (input.answers().size() != activity.getQuestions().size()) {
            throw new BusinessValidationException("Responda todas as questões da atividade, na ordem apresentada.");
        }
        for (int i = 0; i < input.answers().size(); i++) {
            if (input.answers().get(i) >= activity.getQuestions().get(i).getOptions().size()) {
                throw new BusinessValidationException("A resposta deve indicar uma alternativa existente.");
            }
        }
        var submission = new QuizSubmission();
        submission.setActivity(activity);
        submission.setStudent(user);
        submission.getAnswers().addAll(input.answers());
        submission.setSubmittedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        calculateGrade(submission);
        return mapper.toResponse(submissions.saveAndFlush(submission));
    }

    @Transactional
    public SubmissionResponse grade(Long id) {
        var submission = submissions.findForGrading(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));
        requireTeacher(submission.getActivity().getSubject(), currentUser.get());
        if (submission.getGradedAt() != null) throw new SubmissionAlreadyGradedException();
        calculateGrade(submission);
        submission.setGradedBy(currentUser.get());
        return mapper.toResponse(submission);
    }

    private void calculateGrade(QuizSubmission submission) {
        int correct = 0;
        var questions = submission.getActivity().getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            if (submission.getAnswers().get(i) == questions.get(i).getCorrectOption()) correct++;
        }
        submission.setCorrectAnswers(correct);
        submission.setScore(BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP));
        submission.setGradedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public SubmissionResponse result(Long id) {
        var submission = submissions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));
        var user = currentUser.get();
        if (user.getRole() == Role.STUDENT) {
            if (!submission.getStudent().getId().equals(user.getId())
                    || !user.isInActiveSchool(submission.getActivity().getSubject().getClassroom())) {
                throw new AccessDeniedException("Você só pode consultar suas entregas na escola ativa.");
            }
        } else {
            requireTeacher(submission.getActivity().getSubject(), user);
        }
        return mapper.toResponse(submission);
    }

    public List<TeacherActivityResponse> teacherActivities() {
        var user = currentUser.get();
        if (user.getRole() != Role.TEACHER) throw new AccessDeniedException("Esta consulta exige um professor.");
        return activities.findForTeacher(user.getId()).stream()
                .map(activity -> new TeacherActivityResponse(mapper.toResponse(activity),
                        submissions.findByActivityIdOrderBySubmittedAtDescIdDesc(activity.getId()).stream()
                                .map(mapper::toResponse).toList())).toList();
    }

    private Activity activity(Long id) {
        return activities.findById(id).orElseThrow(() -> new ResourceNotFoundException("Atividade não encontrada."));
    }

    private Subject subject(Long id) {
        return subjects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Disciplina não encontrada."));
    }

    private void requireStudent(SchoolUser user) {
        if (user.getRole() != Role.STUDENT) throw new AccessDeniedException("Esta operação exige um aluno.");
    }

    private void requireTeacher(Subject subject, SchoolUser user) {
        access.requireAssignedTeacher(subject.getClassroom(), user);
        if (!subject.hasTeacher(user.getId())) {
            throw new AccessDeniedException("Esta operação exige um professor da disciplina.");
        }
    }

    private void requireReader(Subject subject, SchoolUser user) {
        if (user.getRole() == Role.STUDENT) access.requireAccess(subject.getClassroom(), user);
        else requireTeacher(subject, user);
    }
}

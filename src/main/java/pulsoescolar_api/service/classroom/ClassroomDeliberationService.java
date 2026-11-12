package pulsoescolar_api.service.classroom;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.classroom.DeliberateClassroomRequest;
import pulsoescolar_api.dto.classroom.DeliberationResponse;
import pulsoescolar_api.entity.classroom.*;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.repository.classroom.ClassroomDeliberationRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import pulsoescolar_api.security.user.UserAccessPolicy;
import pulsoescolar_api.service.activity.ActivityRecipientService;

@Service
@RequiredArgsConstructor
public class ClassroomDeliberationService {
    private final CurrentUser currentUser;
    private final UserAccessPolicy accessPolicy;
    private final ClassroomAccessPolicy classroomPolicy;
    private final ClassroomLookupService lookup;
    private final UserRepository users;
    private final ClassroomDeliberationRepository deliberations;
    private final ActivityRecipientService recipients;
    private final EntityManager entityManager;
    private final Clock clock;

    /** Aplica tudo ou nada: qualquer validação que falhar desfaz a transação inteira. */
    @Transactional
    public DeliberationResponse deliberate(DeliberateClassroomRequest request) {
        var actor = currentUser.get();
        accessPolicy.requireCoordinator(actor);
        var origin = lookup.findById(request.classroomId());
        classroomPolicy.requireAccess(origin, actor);
        // A trava serializa confirmações simultâneas; o reenvio encontra a turma já sem alunos (ou encerrada).
        entityManager.lock(origin, LockModeType.PESSIMISTIC_WRITE);

        var enrolled = users.findByClassroomIdAndRoleAndDeletedAtIsNull(origin.getId(), Role.STUDENT).stream()
                .filter(student -> student.isInActiveSchool(origin))
                .collect(Collectors.toMap(SchoolUser::getId, student -> student));
        if (enrolled.isEmpty()) throw new BusinessValidationException("Turmas sem alunos não podem ser deliberadas.");

        var submitted = new HashSet<Long>();
        for (var item : request.students()) {
            if (!submitted.add(item.studentId())) {
                throw new BusinessValidationException("O aluno " + item.studentId() + " foi informado mais de uma vez.");
            }
            if (!enrolled.containsKey(item.studentId())) {
                throw new BusinessValidationException("O aluno " + item.studentId() + " não pertence à turma de origem.");
            }
        }
        if (submitted.size() != enrolled.size()) {
            throw new BusinessValidationException("A deliberação deve conter todos os alunos da turma, cada um exatamente uma vez.");
        }

        var destinations = new HashMap<Long, Classroom>();
        var deliberation = new ClassroomDeliberation();
        deliberation.setClassroom(origin);
        deliberation.setSchool(origin.getSchool());
        deliberation.setAction(request.action());
        deliberation.setPerformedBy(actor);
        deliberation.setPerformedAt(clock.instant());
        for (var item : request.students()) {
            Classroom destination = null;
            if (request.action() == DeliberationAction.GRADUATE) {
                if (item.result() != DeliberationResult.APPROVED) {
                    throw new BusinessValidationException("A formatura exige que todos os alunos estejam aprovados.");
                }
                if (item.destinationClassroomId() != null) {
                    throw new BusinessValidationException("Na formatura os alunos não têm turma de destino.");
                }
            } else {
                destination = destination(item, origin, destinations);
            }
            deliberation.getEntries().add(new DeliberationEntry(enrolled.get(item.studentId()), item.result(), destination));
        }

        for (var entry : deliberation.getEntries()) {
            var student = entry.getStudent();
            student.setClassroom(entry.getDestination());
            if (entry.getDestination() != null) recipients.assignOpenActivities(student);
        }
        if (request.action() == DeliberationAction.GRADUATE) {
            var now = clock.instant();
            origin.setGraduatedAt(now);
            origin.setDeletedAt(now);
        }
        return DeliberationResponse.from(deliberations.saveAndFlush(deliberation));
    }

    private Classroom destination(DeliberateClassroomRequest.StudentResult item, Classroom origin,
                                  Map<Long, Classroom> cache) {
        if (item.destinationClassroomId() == null) {
            throw new BusinessValidationException("Informe a turma de destino do aluno " + item.studentId() + ".");
        }
        if (item.destinationClassroomId().equals(origin.getId())) {
            throw new BusinessValidationException("A turma de destino deve ser diferente da turma de origem.");
        }
        return cache.computeIfAbsent(item.destinationClassroomId(), id -> {
            var destination = lookup.findById(id);
            if (destination.getSchool() == null || origin.getSchool() == null
                    || !destination.getSchool().getId().equals(origin.getSchool().getId())) {
                throw new BusinessValidationException("A turma de destino deve pertencer à mesma escola.");
            }
            return destination;
        });
    }
}

package pulsoescolar_api.service.user.student;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.common.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import pulsoescolar_api.repository.user.StudentTransferRepository;
import pulsoescolar_api.service.classroom.ClassroomLookupService;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class StudentTransferService {
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final StudentTransferRepository transfers;
    private final ClassroomLookupService classrooms;
    private final EntityManager entityManager;
    private final Clock clock;
    private final pulsoescolar_api.service.activity.ActivityRecipientService recipients;

    @Transactional
    public StudentTransferResponse transfer(TransferStudentRequest input) {
        var actor = requireCoordinator();
        var student = users.findByRa(input.ra().strip())
                .filter(user -> user.getRole() == Role.STUDENT && user.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado."));
        entityManager.refresh(student, LockModeType.PESSIMISTIC_WRITE);
        if (student.getDeletedAt() != null) throw new ResourceNotFoundException("Aluno não encontrado.");
        if (!student.belongsToSchool(actor.getSchool().getId())) {
            throw new AccessDeniedException("O aluno deve pertencer à escola do coordenador.");
        }
        if (!student.getFullName().equalsIgnoreCase(input.fullName().strip())) {
            throw new BusinessValidationException("O nome não corresponde ao RA informado.");
        }
        if (student.belongsToSchool(input.schoolId())) {
            throw new BusinessConflictException("O aluno já pertence à escola de destino.");
        }
        var destination = schools.findById(input.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Escola de destino não encontrada."));
        destination.requireActive();
        if (transfers.existsByStudentIdAndStatus(student.getId(), RegistrationStatus.PENDING)) {
            throw new BusinessConflictException("O aluno já possui uma transferência pendente.");
        }
        var transfer = new StudentTransfer();
        transfer.setStudent(student);
        transfer.setOrigin(student.getSchool());
        transfer.setDestination(destination);
        transfer.setRequestedBy(actor);
        transfer.setRequestedAt(clock.instant());
        transfer.setStatus(RegistrationStatus.PENDING);
        return StudentTransferResponse.from(transfers.saveAndFlush(transfer));
    }

    @Transactional(readOnly = true)
    public List<StudentTransferResponse> list(RegistrationStatus status) {
        var actor = requireCoordinator();
        return transfers.findByDestinationIdAndStatusOrderByRequestedAtAscIdAsc(actor.getSchool().getId(), status)
                .stream().filter(transfer -> transfer.getStudent().getDeletedAt() == null)
                .map(StudentTransferResponse::from).toList();
    }

    @Transactional
    public StudentTransferResponse review(Long id, ReviewStudentTransferRequest input) {
        var actor = requireCoordinator();
        var transfer = transfers.findForReview(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transferência não encontrada."));
        if (!actor.getSchool().getId().equals(transfer.getDestination().getId())) {
            throw new AccessDeniedException("Somente a coordenação da escola de destino pode analisar a transferência.");
        }
        if (transfer.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessConflictException("Esta transferência já foi analisada.");
        }
        if (input.status() != RegistrationStatus.APPROVED && input.status() != RegistrationStatus.REJECTED) {
            throw new BusinessValidationException("Informe APPROVED ou REJECTED.");
        }
        var student = transfer.getStudent();
        entityManager.refresh(student, LockModeType.PESSIMISTIC_WRITE);
        if (student.getDeletedAt() != null || !student.belongsToSchool(transfer.getOrigin().getId())) {
            throw new BusinessConflictException("O aluno não está mais ativo na escola de origem.");
        }
        if (input.status() == RegistrationStatus.APPROVED) {
            if (input.classroomId() == null) throw new BusinessValidationException("Informe a sala de destino.");
            var classroom = classrooms.findById(input.classroomId());
            if (classroom.getSchool() == null || !classroom.getSchool().getId().equals(transfer.getDestination().getId())) {
                throw new BusinessValidationException("A sala deve pertencer à escola de destino.");
            }
            student.setSchool(transfer.getDestination());
            student.setClassroom(classroom);
            recipients.assignOpenActivities(student);
        }
        transfer.setStatus(input.status());
        transfer.setReviewedBy(actor);
        transfer.setReviewedAt(clock.instant());
        transfers.flush();
        return StudentTransferResponse.from(transfer);
    }

    private SchoolUser requireCoordinator() {
        var actor = currentUser.get();
        if (actor.getRole() != Role.PEDAGOGICAL_COORDINATOR || actor.getSchool() == null || actor.getDeletedAt() != null) {
            throw new AccessDeniedException("Esta operação exige um coordenador vinculado à escola.");
        }
        return actor;
    }
}

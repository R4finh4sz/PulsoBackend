package pulsoescolar_api.service.subject;

import jakarta.persistence.*;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.subject.*;
import pulsoescolar_api.entity.notification.UserNotification;
import pulsoescolar_api.entity.subject.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.*;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.subject.*;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectInvitationService {
    private final SubjectRepository subjects;
    private final SubjectInvitationRepository invitations;
    private final UserRepository users;
    private final UserNotificationRepository notifications;
    private final CurrentUser currentUser;
    private final ClassroomAccessPolicy access;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public SubjectInvitationResponse create(Long classroomId, Long subjectId, CreateSubjectInvitationRequest input) {
        var subject = subjects.findLockedById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Disciplina não encontrada."));
        if (!subject.getClassroom().getId().equals(classroomId)) {
            throw new ResourceNotFoundException("Disciplina não encontrada nesta turma.");
        }
        var inviter = currentUser.get();
        requireParticipant(subject, inviter);
        var recipient = users.findByEmail(input.email())
                .orElseThrow(() -> new ResourceNotFoundException("Professor cadastrado não encontrado."));
        entityManager.refresh(recipient, LockModeType.PESSIMISTIC_READ);
        if (recipient.getDeletedAt() != null) throw new ResourceNotFoundException("Professor cadastrado não encontrado.");
        requireTeacher(recipient);
        requireSchool(subject, recipient);
        if (subject.hasTeacher(recipient.getId())) {
            throw new BusinessConflictException("Este professor já participa da disciplina.");
        }
        var invitation = invitations.findBySubjectIdAndRecipientId(subjectId, recipient.getId()).orElseGet(SubjectInvitation::new);
        if (invitation.getStatus() == SubjectInvitationStatus.PENDING) {
            throw new BusinessConflictException("Já existe um convite pendente para este professor.");
        }
        var notification = new UserNotification();
        notification.setRecipient(recipient);
        notification.setType("SUBJECT_INVITATION");
        notification.setTitle("Convite de disciplina");
        notification.setMessage("Você recebeu um convite para colaborar em uma disciplina.");
        notification.setCreatedAt(clock.instant());
        invitation.setSubject(subject);
        invitation.setRecipient(recipient);
        invitation.setInviter(inviter);
        invitation.setStatus(SubjectInvitationStatus.PENDING);
        invitation.setCreatedAt(clock.instant());
        invitation.setRespondedAt(null);
        invitation.setNotification(notifications.save(notification));
        return SubjectInvitationResponse.from(invitations.saveAndFlush(invitation));
    }

    public List<SubjectInvitationResponse> mine() {
        var user = currentUser.get();
        requireTeacher(user);
        return invitations.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(SubjectInvitationResponse::from).toList();
    }

    @Transactional
    public SubjectInvitationResponse respond(Long id, RespondSubjectInvitationRequest input) {
        var user = currentUser.get();
        requireTeacher(user);
        if (input.status() != SubjectInvitationStatus.ACCEPTED && input.status() != SubjectInvitationStatus.REJECTED) {
            throw new BusinessValidationException("Informe ACCEPTED ou REJECTED.");
        }
        var found = invitations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado."));
        if (!found.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Somente o destinatário pode responder ao convite.");
        }
        // Envios e respostas bloqueiam primeiro a disciplina, depois o convite e os vínculos.
        var subject = subjects.findLockedById(found.getSubject().getId()).orElseThrow();
        var invitation = invitations.findLockedById(id).orElseThrow();
        entityManager.refresh(invitation);
        if (invitation.getStatus() != SubjectInvitationStatus.PENDING) {
            throw new BusinessConflictException("Este convite já foi respondido.");
        }
        if (input.status() == SubjectInvitationStatus.ACCEPTED) {
            entityManager.refresh(subject.getClassroom(), LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(user, LockModeType.PESSIMISTIC_READ);
            requireTeacher(user);
            requireSchool(subject, user);
            requireParticipant(subject, invitation.getInviter());
            subject.getClassroom().getTeachers().add(user);
            subject.getCollaborators().add(user);
        }
        invitation.setStatus(input.status());
        invitation.setRespondedAt(clock.instant());
        var notification = notifications.findMineForUpdate(invitation.getNotification().getId(), user.getId()).orElseThrow();
        if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
        return SubjectInvitationResponse.from(invitation);
    }

    private void requireParticipant(Subject subject, SchoolUser user) {
        requireTeacher(user);
        requireSchool(subject, user);
        access.requireAssignedTeacher(subject.getClassroom(), user);
        if (!subject.hasTeacher(user.getId())) {
            throw new AccessDeniedException("Somente os professores da disciplina podem enviar convites.");
        }
    }

    private void requireTeacher(SchoolUser user) {
        if (user.getRole() != Role.TEACHER || user.getDeletedAt() != null) {
            throw new AccessDeniedException("Esta operação exige um professor ativo.");
        }
    }

    private void requireSchool(Subject subject, SchoolUser user) {
        if (subject.getClassroom().getDeletedAt() != null) {
            throw new ResourceNotFoundException("Sala não encontrada.");
        }
        var school = subject.getClassroom().getSchool();
        if (school == null || user.getSchool() == null || !school.getId().equals(user.getSchool().getId())) {
            throw new AccessDeniedException("O professor deve pertencer à escola da disciplina.");
        }
    }
}

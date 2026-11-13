package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.entity.notification.UserNotification;
import pulsoescolar_api.repository.user.SchoolInvitationRepository;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolInvitationService {
    private final SchoolInvitationRepository invitations;
    private final UserNotificationRepository notifications;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public InvitationReceipt create(SchoolUser recipient) {
        var actor = currentUser.get();
        if (actor.getRole() != Role.PEDAGOGICAL_COORDINATOR || actor.getSchool() == null) {
            throw new AccessDeniedException("Apenas coordenadores vinculados podem convidar professores.");
        }
        entityManager.refresh(recipient, LockModeType.PESSIMISTIC_WRITE);
        requireTeacher(recipient);
        var school = actor.getSchool();
        if (recipient.belongsToSchool(school.getId())) {
            throw new BusinessConflictException("O professor já possui vínculo com esta escola.");
        }
        var invitation = invitations.findBySchoolIdAndRecipientId(school.getId(), recipient.getId())
                .orElseGet(SchoolInvitation::new);
        var now = clock.instant();
        if (invitation.getStatus() == SchoolInvitationStatus.PENDING && invitation.getExpiresAt().isAfter(now)) {
            throw new BusinessConflictException("Já existe um convite pendente para este professor nesta escola.");
        }
        var notification = new UserNotification();
        notification.setRecipient(recipient);
        notification.setType("SCHOOL_INVITATION");
        notification.setTitle("Convite de escola");
        notification.setMessage("Você recebeu um convite para se vincular a uma escola.");
        notification.setCreatedAt(now);
        invitation.setSchool(school);
        invitation.setRecipient(recipient);
        invitation.setInvitedBy(actor);
        invitation.setStatus(SchoolInvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(Duration.ofDays(2)));
        invitation.setRespondedAt(null);
        invitation.setNotification(notifications.save(notification));
        invitations.saveAndFlush(invitation);
        return new InvitationReceipt(null, recipient.getEmail(), recipient.getRole(), school.getId(),
                invitation.getExpiresAt(), invitation.getId());
    }

    public List<SchoolInvitationResponse> mine() {
        return invitations.findByRecipientIdOrderByCreatedAtDescIdDesc(currentUser.get().getId()).stream()
                .map(SchoolInvitationResponse::from).toList();
    }

    @Transactional
    public SchoolInvitationResponse respond(Long id, RespondSchoolInvitationRequest input) {
        var user = currentUser.get();
        if (input.status() != SchoolInvitationStatus.ACCEPTED && input.status() != SchoolInvitationStatus.REJECTED) {
            throw new BusinessValidationException("Informe ACCEPTED ou REJECTED.");
        }
        // Sending, responding and membership changes for this recipient share the same lock.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireTeacher(user);
        var invitation = invitations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Convite de escola não encontrado."));
        if (!invitation.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Somente o destinatário pode responder ao convite.");
        }
        if (invitation.getStatus() != SchoolInvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(clock.instant())) {
            throw new BusinessConflictException("Este convite já foi respondido ou expirou.");
        }
        if (input.status() == SchoolInvitationStatus.ACCEPTED) {
            invitation.getSchool().requireActive();
            var inviter = invitation.getInvitedBy();
            entityManager.refresh(inviter, LockModeType.PESSIMISTIC_READ);
            if (inviter.getDeletedAt() != null || inviter.getRole() != Role.PEDAGOGICAL_COORDINATOR
                    || !inviter.belongsToSchool(invitation.getSchool().getId())) {
                throw new AccessDeniedException("O remetente não pode mais representar esta escola.");
            }
            if (!user.belongsToSchool(invitation.getSchool().getId())) user.getSchools().add(invitation.getSchool());
            if (user.getSchool() == null) user.setSchool(invitation.getSchool());
        }
        invitation.setStatus(input.status());
        invitation.setRespondedAt(clock.instant());
        var notification = invitation.getNotification();
        if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
        return SchoolInvitationResponse.from(invitation);
    }

    private void requireTeacher(SchoolUser user) {
        if (user.getRole() != Role.TEACHER || user.getDeletedAt() != null) {
            throw new AccessDeniedException("Esta operação exige um professor ativo.");
        }
    }
}

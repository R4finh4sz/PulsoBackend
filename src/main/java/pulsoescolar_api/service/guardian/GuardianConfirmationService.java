package pulsoescolar_api.service.guardian;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.guardian.GuardianDtos.*;
import pulsoescolar_api.entity.guardian.*;
import pulsoescolar_api.entity.user.RegistrationRequest;
import pulsoescolar_api.entity.user.RegistrationStatus;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.exception.invitation.InvitationUnavailableException;
import pulsoescolar_api.repository.guardian.GuardianConfirmationRepository;
import pulsoescolar_api.repository.guardian.GuardianInvitationRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.RegistrationAccessPolicy;
import pulsoescolar_api.service.mail.AccountMailMessages;
import pulsoescolar_api.service.user.InvitationTokenService;

@Service
@RequiredArgsConstructor
public class GuardianConfirmationService {
    public static final int ADULT_AGE = 18;
    public static final int MAX_CORRECTIONS = 3;
    public static final int MAX_RESENDS = 3;
    static final Duration INVITATION_TTL = Duration.ofHours(48);
    static final Duration CORRECTION_TTL = Duration.ofDays(7);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final GuardianConfirmationRepository confirmations;
    private final GuardianInvitationRepository invitations;
    private final InvitationTokenService tokens;
    private final CurrentUser currentUser;
    private final RegistrationAccessPolicy policy;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    @Value("${app.guardian.invitation-base-url:https://pulsoescolar.com.br/guardian}")
    private String invitationBaseUrl;
    @Value("${app.guardian.correction-base-url:https://pulsoescolar.com.br/guardian-correction}")
    private String correctionBaseUrl;


    public boolean requiresGuardian(LocalDate birthDate) {
        return birthDate != null && birthDate.isAfter(LocalDate.now(clock.withZone(ZONE)).minusYears(ADULT_AGE));
    }

    public void requireValidContactIfMinor(LocalDate birthDate, String studentEmail, String name, String email) {
        if (requiresGuardian(birthDate)) validContact(studentEmail, name, email);
    }

    @Transactional
    public void openIfRequired(RegistrationRequest registration, String guardianName, String guardianEmail) {
        if (!requiresGuardian(registration.getBirthDate())) return;
        var contact = validContact(registration.getEmail(), guardianName, guardianEmail);
        var now = clock.instant();
        var confirmation = new GuardianConfirmation();
        confirmation.setRegistration(registration);
        confirmation.setGuardianName(contact[0]);
        confirmation.setGuardianEmail(contact[1]);
        confirmation.setStatus(GuardianStatus.AWAITING_SCHOOL_CHECK);
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        confirmations.save(confirmation);
    }

    @Transactional
    public GuardianPageResponse list(GuardianStatus status, int page, int size) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        confirmations.expireOverdue(clock.instant());
        var pageable = PageRequest.of(page, size);
        var result = actor.getRole() == Role.ADMIN
                ? confirmations.findByStatusOrderByCreatedAtAscIdAsc(status, pageable)
                : confirmations.findByStatusAndRegistrationSchoolIdOrderByCreatedAtAscIdAsc(
                        status, actor.getSchool().getId(), pageable);
        return GuardianPageResponse.from(result.map(this::response));
    }

    @Transactional
    public GuardianConfirmationResponse verify(Long id, VerifyGuardianRequest input) {
        var confirmation = lock(id);
        var actor = reviewer(confirmation);
        if (actor.getRole() == Role.ADMIN && (input.justification() == null || input.justification().isBlank())) {
            throw new BusinessValidationException("Informe a justificativa da atuação de suporte.");
        }
        requireOpenRegistration(confirmation);
        if (confirmation.getStatus() != GuardianStatus.AWAITING_SCHOOL_CHECK) {
            throw new BusinessConflictException("Esta confirmação não está aguardando a conferência da escola.");
        }
        var now = clock.instant();
        confirmation.setCheckedBy(actor);
        confirmation.setCheckedAt(now);
        confirmation.setCheckMethod(input.method().strip());
        confirmation.setCheckJustification(input.justification() == null ? null : input.justification().strip());
        issueInvitation(confirmation);
        return response(confirmation);
    }
    @Transactional
    public GuardianConfirmationResponse requestCorrection(Long id, RequestGuardianCorrectionRequest input) {
        var confirmation = lock(id);
        reviewer(confirmation);
        requireOpenRegistration(confirmation);
        var status = confirmation.getStatus();
        if (status != GuardianStatus.AWAITING_SCHOOL_CHECK && status != GuardianStatus.AWAITING_GUARDIAN
                && status != GuardianStatus.EXPIRED) {
            throw new BusinessConflictException("Esta confirmação não permite solicitar correção neste momento.");
        }
        var now = clock.instant();
        invitations.supersedeOpen(confirmation.getId(), now);
        confirmation.setCorrectionReason(input.reason().strip());
        confirmation.setUpdatedAt(now);
        if (confirmation.getCorrectionCount() >= MAX_CORRECTIONS) {
            confirmation.setCorrectionTokenHash(null);
            confirmation.setCorrectionTokenExpiresAt(null);
            confirmation.setStatus(GuardianStatus.UNDER_REVIEW);
            return response(confirmation);
        }
        var token = tokens.generate();
        confirmation.setCorrectionTokenHash(tokens.hash(token));
        confirmation.setCorrectionTokenExpiresAt(now.plus(CORRECTION_TTL));
        confirmation.setStatus(GuardianStatus.CORRECTION_REQUESTED);
        events.publishEvent(AccountMailMessages.guardianCorrection(confirmation.getRegistration().getEmail(),
                confirmation.getCorrectionReason(), correctionBaseUrl + "/" + token));
        return response(confirmation);
    }

    @Transactional
    public GuardianConfirmationResponse resend(Long id) {
        var confirmation = lock(id);
        reviewer(confirmation);
        requireOpenRegistration(confirmation);
        if (confirmation.getStatus() != GuardianStatus.AWAITING_GUARDIAN
                && confirmation.getStatus() != GuardianStatus.EXPIRED) {
            throw new BusinessConflictException("Não há convite para reenviar neste momento.");
        }
        if (confirmation.getResendCount() >= MAX_RESENDS) {
            throw new BusinessConflictException("Limite de reenvios atingido. Encaminhe o caso para análise.");
        }
        confirmation.setResendCount(confirmation.getResendCount() + 1);
        issueInvitation(confirmation);
        return response(confirmation);
    }

    @Transactional
    public GuardianCorrectionView correctionView(String token) {
        var confirmation = byCorrectionToken(token);
        var registration = confirmation.getRegistration();
        return new GuardianCorrectionView(registration.getFullName(), registration.getSchool().getNome(),
                confirmation.getCorrectionReason(), confirmation.getCorrectionTokenExpiresAt());
    }

    @Transactional
    public GuardianCorrectionReceipt applyCorrection(String token, CorrectGuardianRequest input) {
        var confirmation = byCorrectionToken(token);
        var contact = validContact(confirmation.getRegistration().getEmail(), input.name(), input.email());
        var now = clock.instant();
        invitations.supersedeOpen(confirmation.getId(), now);
        confirmation.setGuardianName(contact[0]);
        confirmation.setGuardianEmail(contact[1]);
        confirmation.setCorrectionCount(confirmation.getCorrectionCount() + 1);
        confirmation.setCorrectionTokenHash(null);
        confirmation.setCorrectionTokenExpiresAt(null);
        confirmation.setCheckedBy(null);
        confirmation.setCheckedAt(null);
        confirmation.setCheckMethod(null);
        confirmation.setCheckJustification(null);
        confirmation.setResendCount(0);
        confirmation.setStatus(GuardianStatus.AWAITING_SCHOOL_CHECK);
        confirmation.setUpdatedAt(now);
        return new GuardianCorrectionReceipt(confirmation.getStatus());
    }
    @Transactional
    public void reconcileAfterBirthDateChange(RegistrationRequest registration) {
        var existing = confirmations.findByRegistrationId(registration.getId());
        if (!requiresGuardian(registration.getBirthDate())) {
            existing.filter(value -> value.getStatus() != GuardianStatus.CONFIRMED).ifPresent(value -> {
                invitations.deleteByConfirmationId(value.getId());
                confirmations.delete(value);
                confirmations.flush();
            });
            return;
        }
        if (existing.isPresent()) return;
        var now = clock.instant();
        var token = tokens.generate();
        var confirmation = new GuardianConfirmation();
        confirmation.setRegistration(registration);
        confirmation.setStatus(GuardianStatus.CORRECTION_REQUESTED);
        confirmation.setCorrectionReason("A escola corrigiu sua data de nascimento. Por ser menor de 18 anos, "
                + "é necessário informar o nome e o e-mail do seu responsável legal.");
        confirmation.setCorrectionTokenHash(tokens.hash(token));
        confirmation.setCorrectionTokenExpiresAt(now.plus(CORRECTION_TTL));
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        confirmations.save(confirmation);
        events.publishEvent(AccountMailMessages.guardianCorrection(registration.getEmail(),
                confirmation.getCorrectionReason(), correctionBaseUrl + "/" + token));
    }
    @Transactional(readOnly = true)
    public void requireConfirmedForApproval(RegistrationRequest registration) {
        if (!requiresGuardian(registration.getBirthDate())) return;
        confirmations.findByRegistrationId(registration.getId())
                .filter(value -> value.getStatus() != GuardianStatus.CONFIRMED)
                .ifPresent(value -> {
                    throw new BusinessConflictException("Aguardando a confirmação do responsável legal do aluno.");
                });
    }

    @Transactional
    public void closeOnRejection(Long registrationId) {
        confirmations.findByRegistrationId(registrationId).ifPresent(value -> {
            invitations.supersedeOpen(value.getId(), clock.instant());
            value.setCorrectionTokenHash(null);
            value.setCorrectionTokenExpiresAt(null);
        });
    }

    private void issueInvitation(GuardianConfirmation confirmation) {
        var now = clock.instant();
        invitations.supersedeOpen(confirmation.getId(), now);
        var token = tokens.generate();
        var invitation = new GuardianInvitation();
        invitation.setConfirmation(confirmation);
        invitation.setTokenHash(tokens.hash(token));
        invitation.setEmail(confirmation.getGuardianEmail());
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitations.save(invitation);
        confirmation.setStatus(GuardianStatus.AWAITING_GUARDIAN);
        confirmation.setUpdatedAt(now);
        var registration = confirmation.getRegistration();
        events.publishEvent(AccountMailMessages.guardianInvitation(invitation.getEmail(), confirmation.getGuardianName(),
                registration.getFullName(), registration.getSchool().getNome(), invitationBaseUrl + "/" + token));
    }

    private GuardianConfirmation lock(Long id) {
        confirmations.expireOverdue(clock.instant());
        return confirmations.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Confirmação do responsável não encontrada."));
    }

    private pulsoescolar_api.entity.user.SchoolUser reviewer(GuardianConfirmation confirmation) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        if (actor.getRole() != Role.ADMIN
                && !actor.getSchool().getId().equals(confirmation.getRegistration().getSchool().getId())) {
            throw new AccessDeniedException("Esta confirmação não pertence à sua escola.");
        }
        return actor;
    }

    private void requireOpenRegistration(GuardianConfirmation confirmation) {
        if (confirmation.getRegistration().getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessConflictException("O cadastro do aluno já foi analisado.");
        }
    }

    private GuardianConfirmation byCorrectionToken(String token) {
        if (token == null || token.isBlank() || token.length() > 200) throw new InvitationUnavailableException();
        var confirmation = confirmations.findForCorrection(tokens.hash(token))
                .orElseThrow(InvitationUnavailableException::new);
        if (confirmation.getStatus() != GuardianStatus.CORRECTION_REQUESTED
                || confirmation.getCorrectionTokenExpiresAt() == null
                || !confirmation.getCorrectionTokenExpiresAt().isAfter(clock.instant())
                || confirmation.getRegistration().getStatus() != RegistrationStatus.PENDING) {
            throw new InvitationUnavailableException();
        }
        return confirmation;
    }

    private String[] validContact(String studentEmail, String name, String email) {
        if (name == null || name.isBlank() || email == null || email.isBlank()) {
            throw new BusinessValidationException("Informe o nome e o e-mail do responsável legal.");
        }
        var cleanName = name.strip();
        var cleanEmail = email.strip().toLowerCase(Locale.ROOT);
        if (cleanName.length() > 100 || cleanEmail.length() > 254
                || !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BusinessValidationException("Confira o nome e o e-mail do responsável legal.");
        }
        if (cleanEmail.equalsIgnoreCase(studentEmail)) {
            throw new BusinessValidationException("O e-mail do responsável deve ser diferente do e-mail do aluno.");
        }
        return new String[] {cleanName, cleanEmail};
    }

    private GuardianConfirmationResponse response(GuardianConfirmation confirmation) {
        return GuardianConfirmationResponse.from(confirmation,
                invitations.findFirstByConfirmationIdOrderByIdDesc(confirmation.getId()).orElse(null));
    }
}

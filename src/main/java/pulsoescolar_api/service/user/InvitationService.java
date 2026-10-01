package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.BusinessConflictException;
import pulsoescolar_api.exception.BusinessValidationException;
import pulsoescolar_api.exception.InvalidVerificationCodeException;
import pulsoescolar_api.exception.InvitationUnavailableException;
import pulsoescolar_api.exception.OperationNotAllowedException;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.exception.TermsVersionChangedException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.TermsRepository;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.InvitationAccessPolicy;
import pulsoescolar_api.service.mail.InvitationMailService;

@Service
@RequiredArgsConstructor
public class InvitationService {
    private static final Duration INVITATION_TTL = Duration.ofDays(2);
    private final UserInvitationRepository invitations;
    private final RegistrationRepository registrations;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final TermsRepository terms;
    private final CurrentUser currentUser;
    private final InvitationAccessPolicy accessPolicy;
    private final InvitationTokenService tokens;
    private final InvitationVerificationService verification;
    private final PasswordEncoder passwords;
    private final InvitationMailService mail;
    private final Validator validator;
    private final Clock clock;
    private final pulsoescolar_api.service.school.SchoolCoordinatorPolicy coordinators;

    @Transactional
    public InvitationReceipt createCoordinator(CreateCoordinatorInvitationRequest input) {
        var actor = currentUser.get();
        accessPolicy.requireCoordinatorInvitation(actor);
        validate(input);
        coordinators.requireVacancy(input.schoolId());
        var school = schools.findById(input.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Escola não encontrada."));
        return create(input.email(), Role.PEDAGOGICAL_COORDINATOR, school, actor);
    }

    @Transactional
    public InvitationReceipt createTeacher(CreateTeacherInvitationRequest input) {
        var actor = currentUser.get();
        accessPolicy.requireTeacherInvitation(actor);
        validate(input);
        return create(input.email(), Role.TEACHER, actor.getSchool(), actor);
    }

    private InvitationReceipt create(String rawEmail, Role role, School school, SchoolUser actor) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        var now = clock.instant();
        if (users.findByEmail(email).isPresent() || registrations.existsByEmail(email)
                || invitations.existsByEmailAndUsedAtIsNullAndExpiresAtAfter(email, now)) {
            throw new BusinessConflictException("Já existe uma conta ou solicitação para este e-mail.");
        }
        String token = tokens.generate();
        var invitation = new UserInvitation();
        invitation.setTokenHash(tokens.hash(token));
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setSchool(school);
        invitation.setInvitedBy(actor);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitations.saveAndFlush(invitation);
        mail.sendInvitation(email, token);
        return new InvitationReceipt(token, email, role, school.getId(), invitation.getExpiresAt());
    }

    @Transactional
    public InvitationDetails open(String token) {
        var invitation = valid(token);
        verification.issueInitialCode(invitation);
        return details(invitation);
    }

    // Invalid codes must commit the attempt counter, as in TwoFactorService.
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public InvitationDetails verify(String token, VerifyInvitationRequest input) {
        validate(input);
        var invitation = valid(token);
        verification.verify(invitation, input.code());
        return details(invitation);
    }

    @Transactional
    public InvitationDetails resend(String token) {
        var invitation = valid(token);
        verification.resend(invitation);
        return details(invitation);
    }

    @Transactional
    public RegistrationReceipt complete(String token, CompleteInvitationRequest input) {
        validate(input);
        var invitation = valid(token);
        if (invitation.getVerifiedAt() == null) {
            throw new OperationNotAllowedException("Valide o código enviado por e-mail antes de continuar.");
        }
        String ra = input.ra().strip();
        if (users.existsByEmailOrRa(invitation.getEmail(), ra)
                || registrations.existsByEmailOrRa(invitation.getEmail(), ra)) {
            throw new BusinessConflictException("Já existe uma conta ou solicitação com este e-mail ou matrícula.");
        }
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessValidationException("A senha deve ter no máximo 72 bytes.");
        }
        var term = terms.lockCurrent();
        if (invitation.getRole() == Role.PEDAGOGICAL_COORDINATOR) {
            coordinators.requireVacancy(invitation.getSchool().getId());
        }
        if (term == null || term.getVersion() == 0) {
            throw new ResourceNotFoundException("Nenhum termo publicado.");
        }
        if (!TermsVersion.label(term.getVersion()).equals(input.termsVersion())) {
            throw new TermsVersionChangedException();
        }
        var request = new RegistrationRequest();
        request.setFullName(input.name().strip());
        request.setRa(ra);
        request.setEmail(invitation.getEmail());
        request.setPasswordHash(passwords.encode(input.password()));
        request.setRole(invitation.getRole());
        request.setSchool(invitation.getSchool());
        request.setTermsVersion(term.getVersion());
        request.setStatus(RegistrationStatus.PENDING);
        request.setRequestedAt(clock.instant());
        registrations.saveAndFlush(request);
        invitation.setUsedAt(clock.instant());
        invitation.setVerificationCodeHash(null);
        return new RegistrationReceipt(request.getId(), request.getStatus());
    }

    private UserInvitation valid(String token) {
        var invitation = invitations.findForUpdate(tokens.hash(token))
                .orElseThrow(() -> new ResourceNotFoundException("Convite inválido."));
        if (invitation.getUsedAt() != null || !invitation.getExpiresAt().isAfter(clock.instant())) {
            throw new InvitationUnavailableException();
        }
        return invitation;
    }

    private InvitationDetails details(UserInvitation invitation) {
        return new InvitationDetails(invitation.getEmail(), invitation.getRole(),
                invitation.getSchool().getId(), invitation.getSchool().getNome(),
                invitation.getExpiresAt(), invitation.getResendAvailableAt(), invitation.getVerifiedAt() != null);
    }

    private void validate(Object input) {
        if (input == null || !validator.validate(input).isEmpty()) {
            throw new BusinessValidationException("Confira os dados obrigatórios do convite.");
        }
    }
}

package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.terms.TermsRepository;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.user.RegistrationAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationReviewService {
    private final RegistrationRepository requests;
    private final UserRepository users;
    private final TermsRepository terms;
    private final CurrentUser currentUser;
    private final RegistrationAccessPolicy policy;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final pulsoescolar_api.service.school.SchoolCoordinatorPolicy coordinators;
    private final StudentTransferRepository transfers;
    private final pulsoescolar_api.service.user.student.StudentTransferService transferService;

    public RegistrationPageResponse list(RegistrationStatus status, Role role, Long schoolId, int page, int size,
                                         RegistrationRequestType type) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        boolean admin = actor.getRole() == Role.ADMIN;
        var roles = admin ? java.util.List.of(Role.PEDAGOGICAL_COORDINATOR) : java.util.List.of(Role.STUDENT, Role.TEACHER);
        if (role != null && !roles.contains(role)
                || !admin && schoolId != null && !schoolId.equals(actor.getSchool().getId())) {
            return RegistrationPageResponse.from(Page.empty(PageRequest.of(page, size)));
        }
        var result = requests.findInbox(status.name(), (role == null ? roles : java.util.List.of(role)).stream()
                        .map(Enum::name).toList(), admin ? schoolId : actor.getSchool().getId(),
                type != RegistrationRequestType.TRANSFER, !admin && type != RegistrationRequestType.REGISTRATION,
                PageRequest.of(page, size));
        return RegistrationPageResponse.from(result.map(entry -> "TRANSFER".equals(entry.getType())
                ? RegistrationResponse.from(transfers.findById(entry.getId()).orElseThrow())
                : RegistrationResponse.from(requests.findById(entry.getId()).orElseThrow())));
    }

    @Transactional
    public RegistrationResponse review(Long id, ReviewRegistrationRequest input) {
        if (input.type() == RegistrationRequestType.TRANSFER) {
            transferService.review(id, new ReviewStudentTransferRequest(input.status(), input.classroomId()));
            return RegistrationResponse.from(transfers.findById(id).orElseThrow());
        }
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        if (input.type() == null && actor.getRole() == Role.PEDAGOGICAL_COORDINATOR
                && transfers.findById(id).filter(transfer -> transfer.getDestination().getId().equals(actor.getSchool().getId())).isPresent()) {
            throw new BusinessConflictException("Informe type REGISTRATION ou TRANSFER para identificar a solicitação.");
        }
        if (input.status() != RegistrationStatus.APPROVED && input.status() != RegistrationStatus.REJECTED) {
            throw new BusinessValidationException("Informe APPROVED ou REJECTED.");
        }
        var request = requests.findForReview(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada."));
        policy.requireAccess(actor, request);
        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessConflictException("Esta solicitação já foi analisada.");
        }
        if (input.status() == RegistrationStatus.APPROVED) {
            request.getSchool().requireActive();
            // Same lock order as registration: terms before persisting a user.
            var term = terms.lockCurrent();
            if (request.getRole() == Role.PEDAGOGICAL_COORDINATOR) {
                coordinators.requireVacancy(request.getSchool().getId());
            }
            if (users.existsByEmailOrRa(request.getEmail(), request.getRa())) {
                throw new BusinessConflictException("Já existe uma conta com este e-mail ou RA.");
            }
            var user = new SchoolUser();
            user.setFullName(request.getFullName());
            user.setBirthDate(request.getBirthDate());
            user.setRa(request.getRa());
            user.setEmail(request.getEmail());
            user.setPasswordHash(request.getPasswordHash());
            user.setRole(request.getRole());
            user.setSchool(request.getSchool());
            user.setProfilePhoto(request.getProfilePhoto());
            user.getAcceptedTermVersions().add(request.getTermsVersion());
            user.setTermsAccepted(term != null && request.getTermsVersion().equals(term.getVersion()));
            request.setUser(users.saveAndFlush(user));
        }
        request.setPasswordHash(null);
        request.setStatus(input.status());
        request.setReviewedAt(clock.instant());
        request.setReviewedBy(actor);
        request.setReviewReason(input.reason() == null ? null : input.reason().strip());
        requests.flush();
        events.publishEvent(pulsoescolar_api.service.mail.AccountMailMessages.reviewed(
                request.getEmail(), request.getRole(), request.getStatus(), request.getReviewReason()));
        return RegistrationResponse.from(request);
    }

    public byte[] photo(Long id, RegistrationRequestType type) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        if (type == RegistrationRequestType.TRANSFER) {
            var transfer = transfers.findById(id)
                    .filter(value -> value.getStudent().getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Transferência não encontrada."));
            if (actor.getRole() != Role.PEDAGOGICAL_COORDINATOR
                    || !actor.getSchool().getId().equals(transfer.getDestination().getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Esta transferência não pertence à sua escola.");
            }
            var photo = transfer.getStudent().getProfilePhoto();
            if (photo == null) throw new ResourceNotFoundException("Foto não encontrada.");
            return photo;
        }
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada."));
        policy.requireAccess(actor, request);
        if (request.getProfilePhoto() == null) throw new ResourceNotFoundException("Foto não encontrada.");
        return request.getProfilePhoto();
    }
}

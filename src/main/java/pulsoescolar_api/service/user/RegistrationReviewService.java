package pulsoescolar_api.service.user;

import pulsoescolar_api.exception.BusinessConflictException;
import pulsoescolar_api.exception.BusinessValidationException;
import pulsoescolar_api.exception.ResourceNotFoundException;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
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
    private final pulsoescolar_api.service.school.SchoolCoordinatorPolicy coordinators;

    public RegistrationPageResponse list(RegistrationStatus status, Role role, Long schoolId, int page, int size) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        Specification<RegistrationRequest> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), status));
            if (actor.getRole() == Role.ADMIN) {
                predicates.add(cb.equal(root.get("role"), Role.PEDAGOGICAL_COORDINATOR));
            } else {
                predicates.add(root.get("role").in(Role.STUDENT, Role.TEACHER));
                predicates.add(cb.equal(root.get("school").get("id"), actor.getSchool().getId()));
            }
            if (role != null) predicates.add(cb.equal(root.get("role"), role));
            if (schoolId != null) predicates.add(cb.equal(root.get("school").get("id"), schoolId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return RegistrationPageResponse.from(requests.findAll(filter,
                PageRequest.of(page, size, Sort.by("requestedAt").ascending().and(Sort.by("id"))))
                .map(RegistrationResponse::from));
    }

    @Transactional
    public RegistrationResponse review(Long id, ReviewRegistrationRequest input) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
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
        return RegistrationResponse.from(request);
    }

    public byte[] photo(Long id) {
        var actor = currentUser.get();
        policy.requireReviewer(actor);
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada."));
        policy.requireAccess(actor, request);
        if (request.getProfilePhoto() == null) throw new ResourceNotFoundException("Foto não encontrada.");
        return request.getProfilePhoto();
    }
}

package pulsoescolar_api.service.terms;

import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.exception.terms.TermsVersionChangedException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.terms.*;
import pulsoescolar_api.entity.terms.TermsOfUse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.terms.TermsRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {
    private final pulsoescolar_api.service.audit.AuditService audit;
    private final TermsRepository terms;
    private final pulsoescolar_api.repository.terms.TermsVersionRepository versions;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final pulsoescolar_api.repository.auth.AuthSessionRepository sessions;
    private final pulsoescolar_api.repository.terms.TermsRevocationRepository revocations;
    private final jakarta.persistence.EntityManager entityManager;
    private final java.time.Clock clock;

    public TermsResponse get() {
        var term = terms.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum termo publicado."));
        requirePublished(term);
        return response(term);
    }

    @Transactional
    public TermsResponse publish(TermsRequest request, boolean create) {
        if (currentUser.get().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
        var term = terms.lockCurrent();
        if (term == null) throw new ResourceNotFoundException("Nenhum termo publicado.");
        if (create && term.getVersion() > 0) {
            throw new BusinessConflictException("Os termos já existem. Use a rota de edição.");
        }
        if (!create) requirePublished(term);
        term.setTitle(request.title().strip());
        term.setContent(request.content());
        term.setVersion(term.getVersion() + 1);
        terms.saveAndFlush(term);
        versions.saveAndFlush(new pulsoescolar_api.entity.terms.TermsVersion(term.getVersion(), term.getTitle(), term.getContent()));
        users.resetTermsAcceptance();
        return response(term);
    }

    @Transactional
    public void accept(AcceptTermsRequest request) {
        var term = terms.lockCurrent();
        requirePublished(term);
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new BusinessValidationException("É necessário aceitar os termos.");
        }
        if (request.version() == null || !pulsoescolar_api.entity.terms.TermsVersion.label(term.getVersion()).equals(request.version())) {
            throw new TermsVersionChangedException();
        }
        var user = currentUser.get();
        boolean firstAcceptance = user.getAcceptedTermVersions().add(term.getVersion());
        user.setTermsAccepted(true);
        if (firstAcceptance) audit.record(pulsoescolar_api.service.audit.AuditEvent.TERMS_ACCEPTED);
    }

    @Transactional
    public void revoke() {
        var user = currentUser.get();
        if (user.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Administradores não podem revogar os termos.");
        }
        // A mesma trava do login impede criar uma sessão durante a revogação.
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        revocations.save(new pulsoescolar_api.entity.terms.TermsRevocation(user, clock.instant()));
        user.setTermsAccepted(false);
        sessions.revokeAll(user.getId());
    }

    public java.util.List<TermsResponse> history() {
        return versions.findAllByOrderByVersionAsc().stream().map(t ->
                new TermsResponse(t.getTitle(), pulsoescolar_api.entity.terms.TermsVersion.label(t.getVersion()), t.getContent())).toList();
    }

    public java.util.List<String> acceptedVersions() {
        return currentUser.get().getTermsAcceptedVersions();
    }

    private void requirePublished(TermsOfUse term) {
        if (term == null || term.getVersion() == 0) {
            throw new ResourceNotFoundException("Nenhum termo publicado.");
        }
    }

    private TermsResponse response(TermsOfUse term) {
        return new TermsResponse(term.getTitle(), pulsoescolar_api.entity.terms.TermsVersion.label(term.getVersion()), term.getContent());
    }
}

package pulsoescolar_api.service.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.AccountDeletionRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountDeletionService {
    private final AccountDeletionRepository requests;
    private final AuthSessionRepository sessions;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;
    private final PasswordEncoder passwords;
    private final Clock clock;

    @Transactional
    public DeletionResponse create(CreateDeletionRequest input) {
        var requester = requireRequester();
        entityManager.refresh(requester, LockModeType.PESSIMISTIC_WRITE);
        requireActive(requester);
        if (requests.existsByRequesterIdAndStatus(requester.getId(), DeletionStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um pedido de exclusão pendente.");
        }
        var request = new AccountDeletionRequest();
        request.setRequester(requester);
        request.setReason(input.reason().strip());
        request.setStatus(DeletionStatus.PENDING);
        request.setRequestedAt(clock.instant());
        return DeletionResponse.from(requests.saveAndFlush(request));
    }

    public List<DeletionResponse> mine() {
        return requests.findByRequesterIdOrderByRequestedAtDesc(requireRequester().getId()).stream()
                .map(DeletionResponse::from).toList();
    }

    public DeletionPageResponse list(DeletionStatus status, int page, int size) {
        var actor = requireReviewer();
        Specification<AccountDeletionRequest> filter = (root, query, cb) -> {
            var state = cb.equal(root.get("status"), status);
            return actor.getRole() == Role.ADMIN ? state : cb.and(state,
                    root.get("requester").get("role").in(Role.STUDENT, Role.TEACHER),
                    cb.equal(root.get("requester").get("school").get("id"), actor.getSchool().getId()));
        };
        return DeletionPageResponse.from(requests.findAll(filter,
                PageRequest.of(page, size, Sort.by("requestedAt").ascending().and(Sort.by("id"))))
                .map(DeletionResponse::from));
    }

    @Transactional
    public DeletionResponse review(Long id, ReviewDeletionRequest input) {
        var actor = requireReviewer();
        if (input.status() != DeletionStatus.APPROVED && input.status() != DeletionStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe APPROVED ou REJECTED.");
        }
        var request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de exclusão não encontrado."));
        var requester = request.getRequester();
        // All submissions and decisions for a requester share the same database lock.
        entityManager.refresh(requester, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(request);
        if (actor.getRole() != Role.ADMIN
                && requester.getRole() != Role.STUDENT && requester.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Somente administradores podem analisar pedidos de coordenadores.");
        }
        if (actor.getRole() != Role.ADMIN && (requester.getSchool() == null
                || !actor.getSchool().getId().equals(requester.getSchool().getId()))) {
            throw new AccessDeniedException("Você não pode analisar pedidos de outra escola.");
        }
        if (request.getStatus() != DeletionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido já foi analisado.");
        }
        requireActive(requester);
        request.setStatus(input.status());
        request.setReviewedAt(clock.instant());
        request.setReviewedBy(actor);
        request.setReviewReason(input.reason().strip());
        if (input.status() == DeletionStatus.APPROVED) {
            String alias = "DeletedUser_" + requester.getId();
            requester.setFullName(alias);
            requester.setRa(alias);
            requester.setEmail(alias + "@deleted.invalid");
            requester.setPasswordHash(passwords.encode(UUID.randomUUID().toString()));
            requester.setDeletedAt(clock.instant());
            requester.setFirstLogin(false);
            requester.setTermsAccepted(false);
            requester.setTwoFactorResendAvailableAt(null);
            sessions.revokeAll(requester.getId());
            // Free text can contain personal information, including in previous rejected requests.
            for (var history : requests.findByRequesterIdOrderByRequestedAtDesc(requester.getId())) {
                history.setReason(alias);
                if (history.getReviewReason() != null) history.setReviewReason(alias);
            }
        }
        entityManager.flush();
        return DeletionResponse.from(request);
    }

    private SchoolUser requireRequester() {
        var actor = currentUser.get();
        if (actor.getRole() != Role.STUDENT && actor.getRole() != Role.TEACHER
                && actor.getRole() != Role.PEDAGOGICAL_COORDINATOR) {
            throw new AccessDeniedException("Apenas alunos, professores e coordenadores podem solicitar exclusão.");
        }
        requireActive(actor);
        return actor;
    }

    private SchoolUser requireReviewer() {
        var actor = currentUser.get();
        if (actor.getRole() != Role.ADMIN && (actor.getRole() != Role.PEDAGOGICAL_COORDINATOR
                || actor.getSchool() == null)) {
            throw new AccessDeniedException("Esta operação exige um coordenador vinculado à escola ou administrador.");
        }
        requireActive(actor);
        return actor;
    }

    private void requireActive(SchoolUser user) {
        if (user.getDeletedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Conta já excluída.");
    }
}

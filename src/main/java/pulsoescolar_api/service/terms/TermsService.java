package pulsoescolar_api.service.terms;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
    private final TermsRepository terms;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public TermsResponse get() {
        var term = terms.findById(1L).orElseThrow();
        requirePublished(term);
        return response(term);
    }

    @Transactional
    public TermsResponse publish(TermsRequest request, boolean create) {
        if (currentUser.get().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
        var term = terms.lockCurrent();
        if (create && term.getVersion() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Os termos já existem. Use a rota de edição.");
        }
        if (!create) requirePublished(term);
        term.setTitle(request.title().strip());
        term.setContent(request.content());
        term.setVersion(term.getVersion() + 1);
        terms.saveAndFlush(term);
        users.resetTermsAcceptance();
        return response(term);
    }

    @Transactional
    public void accept(AcceptTermsRequest request) {
        var term = terms.lockCurrent();
        requirePublished(term);
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "É necessário aceitar os termos.");
        }
        if (request.version() == null || term.getVersion() != request.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Os termos foram atualizados. Consulte a versão atual.");
        }
        currentUser.get().setTermsAccepted(true);
    }

    private void requirePublished(TermsOfUse term) {
        if (term.getVersion() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum termo publicado.");
        }
    }

    private TermsResponse response(TermsOfUse term) {
        return new TermsResponse(term.getTitle(), term.getVersion(), term.getContent());
    }
}

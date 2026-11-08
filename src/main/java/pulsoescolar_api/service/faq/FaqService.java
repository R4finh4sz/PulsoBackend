package pulsoescolar_api.service.faq;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.faq.*;
import pulsoescolar_api.entity.faq.Faq;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.faq.FaqRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {
    private final FaqRepository faqs;
    private final CurrentUser currentUser;

    public List<FaqResponse> list() {
        var role = currentUser.get().getRole();
        return (role == Role.ADMIN ? faqs.findAllByOrderByIdAsc() : faqs.findByTargetRoleOrderByIdAsc(role))
                .stream().map(FaqResponse::from).toList();
    }

    @Transactional
    public FaqResponse create(FaqRequest request) {
        requireAdmin();
        return save(new Faq(), request);
    }

    @Transactional
    public FaqResponse update(Long id, FaqRequest request) {
        requireAdmin();
        return save(find(id), request);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        faqs.delete(find(id));
    }

    private FaqResponse save(Faq faq, FaqRequest request) {
        if (request.targetRole() == Role.ADMIN) {
            throw new BusinessValidationException("O destinatário deve ser PEDAGOGICAL_COORDINATOR, TEACHER ou STUDENT.");
        }
        faq.setTitle(request.title());
        faq.setTargetRole(request.targetRole());
        faq.setMessage(request.message());
        return FaqResponse.from(faqs.saveAndFlush(faq));
    }

    private Faq find(Long id) {
        return faqs.findById(id).orElseThrow(() -> new ResourceNotFoundException("FAQ não encontrado."));
    }

    private void requireAdmin() {
        if (currentUser.get().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
    }
}

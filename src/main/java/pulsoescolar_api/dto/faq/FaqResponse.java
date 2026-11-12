package pulsoescolar_api.dto.faq;

import pulsoescolar_api.entity.faq.Faq;
import pulsoescolar_api.entity.user.Role;

public record FaqResponse(Long id, String title, Role targetRole, String message) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(faq.getId(), faq.getTitle(), faq.getTargetRole(), faq.getMessage());
    }
}

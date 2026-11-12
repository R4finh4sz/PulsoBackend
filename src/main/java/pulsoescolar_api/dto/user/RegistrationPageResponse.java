package pulsoescolar_api.dto.user;

import java.util.List;
import org.springframework.data.domain.Page;

public record RegistrationPageResponse(List<RegistrationResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public static RegistrationPageResponse from(Page<RegistrationResponse> page) {
        return new RegistrationPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

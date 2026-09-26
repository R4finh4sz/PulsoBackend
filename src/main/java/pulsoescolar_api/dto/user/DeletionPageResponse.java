package pulsoescolar_api.dto.user;

import java.util.List;
import org.springframework.data.domain.Page;

public record DeletionPageResponse(List<DeletionResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public static DeletionPageResponse from(Page<DeletionResponse> page) {
        return new DeletionPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

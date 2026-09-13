package pulsoescolar_api.dto.user;

import java.util.List;
import org.springframework.data.domain.Page;

public record UserPageResponse(List<UserResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public static UserPageResponse from(Page<UserResponse> page) {
        return new UserPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

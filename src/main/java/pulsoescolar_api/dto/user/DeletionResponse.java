package pulsoescolar_api.dto.user;

import java.time.Instant;
import pulsoescolar_api.entity.user.*;

public record DeletionResponse(Long id, Long userId, Role role, Long studentId, String fullName, String ra, String email,
        String reason, DeletionStatus status, Instant requestedAt, Instant reviewedAt,
        Long reviewedById, String reviewReason) {
    public static DeletionResponse from(AccountDeletionRequest request) {
        var student = request.getRequester();
        return new DeletionResponse(request.getId(), student.getId(), student.getRole(),
                student.getRole() == Role.STUDENT ? student.getId() : null, student.getFullName(),
                student.getRa(), student.getEmail(), request.getReason(), request.getStatus(),
                request.getRequestedAt(), request.getReviewedAt(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getId(), request.getReviewReason());
    }
}

package pulsoescolar_api.dto.user;

import java.time.Instant;
import java.time.LocalDate;
import pulsoescolar_api.entity.user.*;

public record RegistrationResponse(Long id, String name, LocalDate birthDate, String ra, String email,
        Role role, Long schoolId, String schoolName, RegistrationStatus status,
        Instant requestedAt, Instant reviewedAt, Long reviewedById, String reviewReason,
        Long userId, boolean hasPhoto) {
    public static RegistrationResponse from(RegistrationRequest request) {
        return new RegistrationResponse(request.getId(), request.getFullName(), request.getBirthDate(),
                request.getRa(), request.getEmail(), request.getRole(), request.getSchool().getId(),
                request.getSchool().getNome(), request.getStatus(), request.getRequestedAt(),
                request.getReviewedAt(), request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewReason(), request.getUser() == null ? null : request.getUser().getId(),
                request.getProfilePhoto() != null);
    }
}

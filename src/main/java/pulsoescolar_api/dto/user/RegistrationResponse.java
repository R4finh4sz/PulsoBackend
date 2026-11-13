package pulsoescolar_api.dto.user;

import java.time.Instant;
import java.time.LocalDate;
import pulsoescolar_api.entity.user.*;

public record RegistrationResponse(Long id, String name, LocalDate birthDate, String ra, String email,
        Role role, Long schoolId, String schoolName, RegistrationStatus status,
        Instant requestedAt, Instant reviewedAt, Long reviewedById, String reviewReason,
        Long userId, boolean hasPhoto, RegistrationRequestType type, Long originSchoolId) {
    public static RegistrationResponse from(RegistrationRequest request) {
        return new RegistrationResponse(request.getId(), request.getFullName(), request.getBirthDate(),
                request.getRa(), request.getEmail(), request.getRole(), request.getSchool().getId(),
                request.getSchool().getNome(), request.getStatus(), request.getRequestedAt(),
                request.getReviewedAt(), request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewReason(), request.getUser() == null ? null : request.getUser().getId(),
                request.getProfilePhoto() != null, RegistrationRequestType.REGISTRATION, null);
    }

    public static RegistrationResponse from(StudentTransfer transfer) {
        var student = transfer.getStudent();
        return new RegistrationResponse(transfer.getId(), student.getFullName(), student.getBirthDate(), student.getRa(),
                student.getEmail(), student.getRole(), transfer.getDestination().getId(), transfer.getDestination().getNome(),
                transfer.getStatus(), transfer.getRequestedAt(), transfer.getReviewedAt(),
                transfer.getReviewedBy() == null ? null : transfer.getReviewedBy().getId(), null,
                student.getId(), student.getProfilePhoto() != null, RegistrationRequestType.TRANSFER, transfer.getOrigin().getId());
    }
}

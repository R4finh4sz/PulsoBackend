package pulsoescolar_api.dto.user;

import java.time.Instant;
import pulsoescolar_api.entity.user.*;

public record StudentTransferResponse(Long id, Long studentId, String fullName, String ra,
        Long originSchoolId, Long destinationSchoolId, RegistrationStatus status,
        Instant requestedAt, Instant reviewedAt) {
    public static StudentTransferResponse from(StudentTransfer transfer) {
        var student = transfer.getStudent();
        return new StudentTransferResponse(transfer.getId(), student.getId(), student.getFullName(), student.getRa(),
                transfer.getOrigin().getId(), transfer.getDestination().getId(), transfer.getStatus(),
                transfer.getRequestedAt(), transfer.getReviewedAt());
    }
}

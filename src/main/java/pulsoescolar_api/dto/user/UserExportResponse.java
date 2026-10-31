package pulsoescolar_api.dto.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pulsoescolar_api.dto.notification.NotificationResponse;
import pulsoescolar_api.dto.school.SchoolSearchResponse.SchoolOption;
import pulsoescolar_api.entity.user.DeletionStatus;
import pulsoescolar_api.entity.user.RegistrationStatus;
import pulsoescolar_api.entity.subject.SubjectInvitationStatus;

public record UserExportResponse(Instant exportedAt, UserResponse user, LocalDate birthDate, byte[] profilePhoto,
        boolean termsAccepted, List<String> termsAcceptedVersions, List<SchoolOption> schools,
        List<Classroom> classrooms, Registration registration, List<Deletion> deletionRequests,
        List<NotificationResponse> notifications, List<Invitation> receivedInvitations) {
    public record Classroom(Long id, String name, String identifier) {}
    public record Registration(Long id, String fullName, String ra, String email, LocalDate birthDate,
            Long schoolId, Long termsVersion, byte[] profilePhoto, RegistrationStatus status,
            Instant requestedAt, Instant reviewedAt, String reviewReason) {}
    public record Deletion(Long id, String reason, DeletionStatus status, Instant requestedAt,
            Instant reviewedAt, String reviewReason) {}
    public record Invitation(Long id, Long subjectId, SubjectInvitationStatus status,
            Instant createdAt, Instant respondedAt) {}
}

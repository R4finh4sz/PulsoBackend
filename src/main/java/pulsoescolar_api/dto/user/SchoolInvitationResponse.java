package pulsoescolar_api.dto.user;

import java.time.Instant;
import pulsoescolar_api.entity.user.SchoolInvitation;
import pulsoescolar_api.entity.user.SchoolInvitationStatus;

public record SchoolInvitationResponse(Long id, Long schoolId, String schoolName,
        SchoolInvitationStatus status, Instant createdAt, Instant expiresAt, Instant respondedAt) {
    public static SchoolInvitationResponse from(SchoolInvitation invitation) {
        return new SchoolInvitationResponse(invitation.getId(), invitation.getSchool().getId(),
                invitation.getSchool().getNome(), invitation.getStatus(), invitation.getCreatedAt(),
                invitation.getExpiresAt(), invitation.getRespondedAt());
    }
}

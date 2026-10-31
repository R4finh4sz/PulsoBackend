package pulsoescolar_api.dto.subject;

import java.time.Instant;
import pulsoescolar_api.entity.subject.*;

public record SubjectInvitationResponse(Long id, String email, Long subjectId, Long classroomId,
        String subjectName, String inviterName, SubjectInvitationStatus status, Instant createdAt, Instant respondedAt) {
    public static SubjectInvitationResponse from(SubjectInvitation invitation) {
        return new SubjectInvitationResponse(invitation.getId(), invitation.getRecipient().getEmail(),
                invitation.getSubject().getId(), invitation.getSubject().getClassroom().getId(),
                invitation.getSubject().getName(), invitation.getInviter().getFullName(),
                invitation.getStatus(), invitation.getCreatedAt(), invitation.getRespondedAt());
    }
}

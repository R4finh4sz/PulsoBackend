package pulsoescolar_api.dto.subject;

import jakarta.validation.constraints.NotNull;
import pulsoescolar_api.entity.subject.SubjectInvitationStatus;

public record RespondSubjectInvitationRequest(@NotNull SubjectInvitationStatus status) {}

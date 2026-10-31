package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.NotNull;
import pulsoescolar_api.entity.user.SchoolInvitationStatus;

public record RespondSchoolInvitationRequest(@NotNull SchoolInvitationStatus status) {}

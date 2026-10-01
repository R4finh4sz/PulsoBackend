package pulsoescolar_api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeacherInvitationRequest(@NotBlank @Email @Size(max = 254) String email) {}

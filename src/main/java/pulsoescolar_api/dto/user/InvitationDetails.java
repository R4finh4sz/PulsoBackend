package pulsoescolar_api.dto.user;

import java.time.Instant;
import pulsoescolar_api.entity.user.Role;

public record InvitationDetails(String email, Role role, Long schoolId, String schoolName,
        Instant expiresAt, Instant resendAvailableAt, boolean verified) {}
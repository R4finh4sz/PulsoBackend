package pulsoescolar_api.dto.user;

import java.time.Instant;
import pulsoescolar_api.entity.user.Role;

public record InvitationReceipt(String token, String email, Role role, Long schoolId, Instant expiresAt) {
    @Override public String toString() { return "InvitationReceipt[REDACTED]"; }
}

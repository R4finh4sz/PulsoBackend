package pulsoescolar_api.dto.auth;

import java.time.Instant;
import pulsoescolar_api.entity.user.Role;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, LoginUser user) {
    public record LoginUser(Role role, Long classroomId, Long schoolId, boolean firstLogin, boolean termsAccepted, java.util.List<String> termsAcceptedVersions) {}

    @Override public String toString() { return "LoginResponse[accessToken=REDACTED, expiresAt=" + expiresAt + "]"; }
}

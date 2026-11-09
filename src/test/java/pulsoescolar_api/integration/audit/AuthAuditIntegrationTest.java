package pulsoescolar_api.integration.audit;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class AuthAuditIntegrationTest extends HttpIntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired pulsoescolar_api.repository.auth.AuthSessionRepository sessions;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @Test void logsFailedLoginEvenWhenAuthenticationRollsBack() {
        var user = criarUsuario(Role.STUDENT);
        long before = count("LOGIN_FAILED", null);
        assertThat(request(POST, "/api/auth/login", null, Map.of("email", user.getEmail(), "password", "WrongPassword123"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("LOGIN_FAILED", null)).isEqualTo(before + 1);
        var login = login(user.getEmail());
        assertThat(count("LOGIN_SUCCEEDED", user.getId())).isEqualTo(1);
        assertThat(request(POST, "/api/auth/logout", login.accessToken(), null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("LOGOUT", user.getId())).isEqualTo(1);
    }

    @Test void recordsRecoveryRequestsIncludingCooldownAndUnknownAccounts() {
        var user = criarUsuario(Role.STUDENT);
        long unknownBefore = count("PASSWORD_RECOVERY_REQUESTED", null);
        for (String email : new String[]{user.getEmail(), user.getEmail(), "unknown-recovery@example.com"}) {
            assertThat(request(POST, "/api/auth/password-recovery/request", null,
                    Map.of("email", email), String.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        assertThat(count("PASSWORD_RECOVERY_REQUESTED", user.getId())).isEqualTo(2);
        assertThat(count("PASSWORD_RECOVERY_REQUESTED", null)).isEqualTo(unknownBefore + 1);
    }

    @Test void loginWithTwoFactorIsOnlyCompletedAfterVerification() throws Exception {
        var user = criarUsuario(Role.ADMIN);
        autenticar(user);
        assertThat(count("LOGIN_2FA_PENDING", user.getId())).isEqualTo(1);
        assertThat(count("TWO_FACTOR_SUCCEEDED", user.getId())).isEqualTo(1);
        assertThat(count("LOGIN_SUCCEEDED", user.getId())).isEqualTo(1);
    }

    @Test void recordsRefreshAndReplayWithoutStoringCredentials() {
        var user = criarUsuario(Role.STUDENT);
        var login = login(user.getEmail());
        var body = Map.of("refreshToken", login.refreshToken());
        assertThat(request(POST, "/api/auth/refresh", null, body, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("TOKEN_REFRESHED", user.getId())).isEqualTo(1);
        long failures = count("TOKEN_REFRESH_FAILED", null);
        assertThat(request(POST, "/api/auth/refresh", null, body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("REFRESH_TOKEN_REUSED", user.getId())).isEqualTo(1);
        assertThat(count("TOKEN_REFRESH_FAILED", null)).isEqualTo(failures + 1);
        var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE LOWER(table_name)='authentication_events'", String.class);
        assertThat(columns.stream().map(String::toLowerCase)).containsExactlyInAnyOrder("id", "occurred_at", "event_type", "actor_user_id");
    }

    @Test void expiredTwoFactorAttemptIsAuditedWithoutCompletingLogin() {
        var user = criarUsuario(Role.ADMIN);
        var login = login(user.getEmail());
        var jwt = decoder.decode(login.accessToken());
        var session = sessions.findById(java.util.UUID.fromString(jwt.getId())).orElseThrow();
        session.setCodeExpiresAt(java.time.Instant.now().minusSeconds(60));
        sessions.saveAndFlush(session);
        assertThat(request(POST, "/api/auth/2fa/verify", login.accessToken(), Map.of("code", "123456"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("TWO_FACTOR_FAILED", user.getId())).isEqualTo(1);
        assertThat(count("LOGIN_SUCCEEDED", user.getId())).isZero();
    }

    private LoginResponse login(String email) {
        var response = request(POST, "/api/auth/login", null, Map.of("email", email, "password", "SenhaTeste123"), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private long count(String event, Long userId) {
        if (userId == null) return jdbc.queryForObject("SELECT COUNT(*) FROM authentication_events WHERE event_type=? AND actor_user_id IS NULL", Long.class, event);
        return jdbc.queryForObject("SELECT COUNT(*) FROM authentication_events WHERE event_type=? AND actor_user_id=?", Long.class, event, userId);
    }
}

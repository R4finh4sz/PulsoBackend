package pulsoescolar_api.integration.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import pulsoescolar_api.repository.auth.*;
import pulsoescolar_api.repository.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

@TestPropertySource(properties = {"app.auth.session-ttl=50m", "app.auth.refresh-ttl=10m"})
class RefreshTokenIntegrationTest extends HttpIntegrationSupport {
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired Clock clock;

    @Test void renewsOnlyOnceForTenMinutesAndStoresOnlyHashes() throws Exception {
        var login = login();
        assertThat(login.refreshExpiresAt()).isAfter(login.expiresAt());
        assertThat(login.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(login.refreshToken().getBytes(StandardCharsets.UTF_8)));
        assertThat(tokens.findById(hash)).isPresent();
        assertThat(tokens.findById(login.refreshToken())).isEmpty();
        var old = decoder.decode(login.accessToken());
        assertThat(java.time.Duration.between(old.getIssuedAt(), old.getExpiresAt())).isEqualTo(java.time.Duration.ofMinutes(50));
        assertThat(login.refreshExpiresAt()).isEqualTo(old.getIssuedAt().plusSeconds(3600));
        var expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().claims(claims -> claims.putAll(old.getClaims()))
                        .issuedAt(clock.instant().minusSeconds(120)).expiresAt(clock.instant().minusSeconds(60)).build())).getTokenValue();
        assertThat(request(GET, "/api/me/export", expired, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var refreshed = refresh(login.refreshToken());
        assertThat(refreshed.refreshToken()).isNull();
        assertThat(refreshed.refreshExpiresAt()).isNull();
        var renewed = decoder.decode(refreshed.accessToken());
        assertThat(java.time.Duration.between(renewed.getIssuedAt(), renewed.getExpiresAt())).isEqualTo(java.time.Duration.ofMinutes(10));
        assertThat(request(GET, "/api/me/export", refreshed.accessToken(), null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tokens.findById(hash).orElseThrow().getUsedAt()).isNotNull();
        var session = sessions.findById(UUID.fromString(renewed.getId())).orElseThrow();
        assertThat(session.getExpiresAt()).isEqualTo(renewed.getExpiresAt());
        session.setExpiresAt(clock.instant().minusSeconds(1));
        sessions.saveAndFlush(session);
        assertThat(request(GET, "/api/me/export", refreshed.accessToken(), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void renewalNeverExceedsTheOriginalSessionDeadline() {
        var login = login();
        var jwt = decoder.decode(login.accessToken());
        var session = sessions.findById(UUID.fromString(jwt.getId())).orElseThrow();
        var deadline = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(120);
        session.setExpiresAt(deadline);
        sessions.saveAndFlush(session);
        var renewed = refresh(login.refreshToken());
        assertThat(renewed.expiresAt()).isEqualTo(deadline);
        assertThat(renewed.refreshToken()).isNull();
    }

    @Test void secondRenewalIsRejectedAndRevokesAdditionalAccess() {
        var first = login();
        var second = refresh(first.refreshToken());
        assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", first.refreshToken()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(GET, "/api/me/export", second.accessToken(), null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void newLoginWithoutTwoFactorRevokesPreviousAccessAndRefreshOnlyForThatUser() {
        var first = login();
        var jwt = decoder.decode(first.accessToken());
        var account = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow();
        var otherUser = login();
        var second = request(POST, "/api/auth/login", null,
                Map.of("email", account.getEmail(), "password", "SenhaTeste123"), LoginResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(GET, "/api/me/export", first.accessToken(), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", first.refreshToken()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(GET, "/api/me/export", second.getBody().accessToken(), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(request(GET, "/api/me/export", otherUser.accessToken(), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        refresh(second.getBody().refreshToken());
    }

    @Test void logoutAndTermsRevocationInvalidateRefreshTokens() {
        for (String path : List.of("/api/auth/logout", "/api/terms/revoke")) {
            var login = login();
            assertThat(request(POST, path, login.accessToken(), null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", login.refreshToken()), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test void refusesExpiredDeletedAndUnverifiedSessions() {
        for (String state : List.of("expired", "deleted", "pending")) {
            var login = login();
            var jwt = decoder.decode(login.accessToken());
            var session = sessions.findById(UUID.fromString(jwt.getId())).orElseThrow();
            if (state.equals("expired")) { session.setExpiresAt(clock.instant().minusSeconds(1)); sessions.saveAndFlush(session); }
            if (state.equals("pending")) { session.setTwoFactorVerified(false); sessions.saveAndFlush(session); }
            if (state.equals("deleted")) {
                var user = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow();
                user.setDeletedAt(clock.instant()); users.saveAndFlush(user);
            }
            assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", login.refreshToken()), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test void validatesPayloadAndRejectsUnknownSecrets() {
        assertThat(request(POST, "/api/auth/refresh", null, Map.of(), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", "invalid"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(POST, "/api/auth/refresh", null, Map.of("refreshToken", "x".repeat(43)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private LoginResponse login() {
        var account = criarUsuario(Role.STUDENT);
        var response = request(POST, "/api/auth/login", null,
                Map.of("email", account.getEmail(), "password", "SenhaTeste123"), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    private LoginResponse refresh(String token) {
        var response = request(POST, "/api/auth/refresh", null, Map.of("refreshToken", token), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }
}

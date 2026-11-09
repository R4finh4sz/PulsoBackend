package pulsoescolar_api.integration.user;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import pulsoescolar_api.config.JwtConfig;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.terms.TermsRevocationRepository;
import pulsoescolar_api.repository.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class TermsRevocationIntegrationTest extends HttpIntegrationSupport {
    private static final String PATH = "/api/terms/revoke";
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired TermsRevocationRepository revocations;
    @Autowired JwtEncoder encoder;
    @Autowired Clock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void revokesForNonAdminRolesPreservingHistoryAndInvalidatingAllOwnTokens(Role role) {
        var account = criarUsuario(role);
        account.setTermsAccepted(true);
        account.setAcceptedTermVersions(new java.util.HashSet<>(Set.of(1L, 2L)));
        users.saveAndFlush(account);
        var first = token(account, true);
        var second = token(account, true);
        var other = criarUsuario(Role.STUDENT);
        other.setTermsAccepted(true);
        users.saveAndFlush(other);
        var otherToken = token(other, true);
        var before = clock.instant();
        var response = request(POST, PATH + "?userId=" + other.getId(), first, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var changed = users.findById(account.getId()).orElseThrow();
        assertThat(changed.isTermsAccepted()).isFalse();
        assertThat(jdbc.queryForList("SELECT version FROM user_terms_acceptances WHERE user_id = ? ORDER BY version",
                Long.class, account.getId())).containsExactly(1L, 2L);
        assertThat(revocations.findAll()).filteredOn(row -> row.getUser().getId().equals(account.getId()))
                .singleElement().satisfies(row -> assertThat(row.getRevokedAt()).isBetween(before, clock.instant()));
        assertThat(request(GET, "/api/me/export", first, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(GET, "/api/me/export", second, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(POST, PATH, first, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(GET, "/api/me/export", otherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(other.getId()).orElseThrow().isTermsAccepted()).isTrue();
    }

    @Test void rejectsAdminWithoutChangingAcceptanceHistoryOrSessions() {
        var account = criarUsuario(Role.ADMIN);
        account.setTermsAccepted(true);
        account.setAcceptedTermVersions(new java.util.HashSet<>(Set.of(1L)));
        users.saveAndFlush(account);
        var accessToken = token(account, true);
        assertThat(request(POST, PATH, accessToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var unchanged = users.findById(account.getId()).orElseThrow();
        assertThat(unchanged.isTermsAccepted()).isTrue();
        assertThat(jdbc.queryForList("SELECT version FROM user_terms_acceptances WHERE user_id = ?", Long.class,
                account.getId())).containsExactly(1L);
        assertThat(revocations.findAll()).noneMatch(row -> row.getUser().getId().equals(account.getId()));
        assertThat(request(GET, "/api/me/export", accessToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void rejectsAnonymousAndPendingTwoFactorWithoutChangingUser() {
        assertThat(request(POST, PATH, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var account = criarUsuario(Role.ADMIN);
        account.setTermsAccepted(true);
        users.saveAndFlush(account);
        var pending = token(account, false);
        assertThat(request(POST, PATH, pending, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(users.findById(account.getId()).orElseThrow().isTermsAccepted()).isTrue();
        assertThat(revocations.findAll()).noneMatch(row -> row.getUser().getId().equals(account.getId()));
    }

    private String token(SchoolUser user, boolean verified) {
        var now = clock.instant();
        var session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUser(user);
        session.setCreatedAt(now); session.setExpiresAt(now.plusSeconds(3600));
        session.setTwoFactorVerified(verified);
        sessions.saveAndFlush(session);
        var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(user.getId().toString()).id(session.getId().toString()).issuedAt(now)
                .expiresAt(session.getExpiresAt()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}

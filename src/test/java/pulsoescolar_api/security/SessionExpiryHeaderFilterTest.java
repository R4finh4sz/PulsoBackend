package pulsoescolar_api.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pulsoescolar_api.security.auth.SessionExpiryHeaderFilter;
import static org.assertj.core.api.Assertions.assertThat;

class SessionExpiryHeaderFilterTest {
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private final SessionExpiryHeaderFilter filter = new SessionExpiryHeaderFilter(Clock.fixed(now, ZoneOffset.UTC));

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(longs = {61, 60, 1, 0, -1})
    void warnsOnlyDuringLastMinuteOfValidAccess(long remaining) throws Exception {
        authenticate(remaining, "ROLE_STUDENT");
        var response = request();
        assertThat(response.getHeader("X-Session-Expires-At"))
                .isEqualTo(remaining > 0 ? now.plusSeconds(remaining).toString() : null);
        assertThat(response.getHeader("X-Session-Warning"))
                .isEqualTo(remaining > 0 && remaining <= 60 ? "expiring" : null);
    }

    @Test void doesNotWarnAnonymousOrPendingTwoFactorRequests() throws Exception {
        assertThat(request().getHeaderNames()).isEmpty();
        authenticate(30, "TWO_FACTOR_PENDING");
        assertThat(request().getHeaderNames()).isEmpty();
    }

    private void authenticate(long remaining, String authority) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("1")
                .issuedAt(now.minusSeconds(1800)).expiresAt(now.plusSeconds(remaining)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(authority))));
    }

    private MockHttpServletResponse request() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), response, new MockFilterChain());
        return response;
    }
}

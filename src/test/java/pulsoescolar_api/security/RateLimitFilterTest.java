package pulsoescolar_api.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.web.*;
import pulsoescolar_api.config.RateLimitProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-11-08T12:00:00Z");
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void limitsByUserAcrossTokensAndSeparatesUsersOnSameWifi() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(2, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock);
        assertThat(request(filter, "/api/faqs", "10.0.0.1", "GET").getStatus()).isEqualTo(200);
        assertThat(request(filter, "/api/teacher/classrooms", "10.0.0.1", "GET").getStatus()).isEqualTo(200);
        var blocked = request(filter, "/api/me/export", "10.0.0.1", "GET");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(request(filter, "/api/faqs", "10.0.0.2", "GET").getStatus()).isEqualTo(200);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(request(filter, "/api/faqs", "10.0.0.1", "GET").getStatus()).isEqualTo(200);
    }

    @Test void authenticationHasStricterSharedLimitAndOptionsAreNotCounted() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(10, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock);
        request(filter, "/api/auth/login", "10.0.0.1", "OPTIONS");
        assertThat(request(filter, "/api/auth/login", "10.0.0.1", "POST").getStatus()).isEqualTo(200);
        assertThat(request(filter, "/api/auth/refresh", "10.0.0.1", "POST").getStatus()).isEqualTo(429);
        assertThat(request(filter, "/api/faqs", "10.0.0.1", "GET").getStatus()).isEqualTo(200);
    }

    @Test void publicRequestsShareIpLimitAcrossCookiesAndIgnoreForgedForwardedHeaders() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(10, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock);
        var first = anonymous(filter, "192.168.1.10");
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getHeader("Set-Cookie")).isNull();
        var blocked = anonymous(filter, "192.168.1.10");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(anonymous(filter, "192.168.1.11").getStatus()).isEqualTo(200);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(anonymous(filter, "192.168.1.10").getStatus()).isEqualTo(200);
    }

    @Test void publicAndAuthenticatedCountersAreIndependentAndUserFollowsIpChanges() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(10, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock);
        assertThat(anonymous(filter, "192.168.1.10").getStatus()).isEqualTo(200);
        assertThat(anonymous(filter, "192.168.1.10").getStatus()).isEqualTo(429);
        assertThat(request(filter, "/api/auth/refresh", "42", "POST").getStatus()).isEqualTo(200);
        var differentIp = new MockHttpServletRequest("POST", "/api/auth/refresh");
        differentIp.setRemoteAddr("192.168.1.11");
        var response = new MockHttpServletResponse();
        filter.doFilter(differentIp, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(request(filter, "/api/auth/refresh", "43", "POST").getStatus()).isEqualTo(200);
    }

    @Test void publicGeneralLimitIsSharedAcrossRoutesAndOptionsAreSkipped() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(1, Duration.ofMinutes(1), 10, Duration.ofMinutes(1)), clock);
        SecurityContextHolder.clearContext();
        for (var entry : java.util.List.of(
                new String[]{"OPTIONS", "/api/auth/register", "200"},
                new String[]{"GET", "/api/schools/search", "200"},
                new String[]{"POST", "/api/students", "429"})) {
            var request = new MockHttpServletRequest(entry[0], entry[1]);
            request.setRemoteAddr("192.168.1.10");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(Integer.parseInt(entry[2]));
        }
    }

    private MockHttpServletResponse anonymous(RateLimitFilter filter, String ip) throws Exception {
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        request.setCookies(new Cookie("pulso_device", java.util.UUID.randomUUID().toString()));
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        request.addHeader("X-Real-IP", java.util.UUID.randomUUID().toString());
        request.addHeader("Forwarded", "for=" + java.util.UUID.randomUUID());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RateLimitProperties(0, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, Duration.ZERO, 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletResponse request(RateLimitFilter filter, String path, String userId, String method) throws Exception {
        var jwt = Jwt.withTokenValue(java.util.UUID.randomUUID().toString()).header("alg", "HS256").subject(userId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

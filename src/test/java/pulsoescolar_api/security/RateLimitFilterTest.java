package pulsoescolar_api.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import jakarta.servlet.http.Cookie;
import javax.crypto.spec.SecretKeySpec;
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
    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "HmacSHA256");
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void limitsByUserAcrossTokensAndSeparatesUsersOnSameWifi() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(2, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock, key);
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
        var filter = new RateLimitFilter(new RateLimitProperties(10, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock, key);
        request(filter, "/api/auth/login", "10.0.0.1", "OPTIONS");
        assertThat(request(filter, "/api/auth/login", "10.0.0.1", "POST").getStatus()).isEqualTo(200);
        assertThat(request(filter, "/api/auth/refresh", "10.0.0.1", "POST").getStatus()).isEqualTo(429);
        assertThat(request(filter, "/api/faqs", "10.0.0.1", "GET").getStatus()).isEqualTo(200);
    }

    @Test void publicRequestsUseSignedDeviceCookiesAndNotSharedIp() throws Exception {
        when(clock.instant()).thenReturn(now);
        var filter = new RateLimitFilter(new RateLimitProperties(10, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)), clock, key);
        var first = anonymous(filter, null);
        var cookie = deviceCookie(first);
        assertThat(first.getHeader("Set-Cookie")).contains("HttpOnly", "Path=/api");
        assertThat(anonymous(filter, cookie).getStatus()).isEqualTo(429);
        assertThat(anonymous(filter, null).getStatus()).isEqualTo(200);
        var forged = new Cookie("pulso_device", cookie.getValue().substring(0, 37) + "x".repeat(43));
        assertThat(anonymous(filter, forged).getHeader("Set-Cookie")).isNotNull();
    }

    private Cookie deviceCookie(MockHttpServletResponse response) {
        var value = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2);
        return new Cookie(value[0], value[1]);
    }

    private MockHttpServletResponse anonymous(RateLimitFilter filter, Cookie cookie) throws Exception {
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.10");
        if (cookie != null) request.setCookies(cookie);
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

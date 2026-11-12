package pulsoescolar_api.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class SessionExpiryHeaderFilter extends OncePerRequestFilter {
    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()
                && jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("TWO_FACTOR_PENDING"))) {
            var expiresAt = jwt.getToken().getExpiresAt();
            var now = clock.instant();
            if (expiresAt != null && expiresAt.isAfter(now)) {
                response.setHeader("X-Session-Expires-At", expiresAt.toString());
                if (!expiresAt.isAfter(now.plusSeconds(60))) {
                    response.setHeader("X-Session-Warning", "expiring");
                }
            }
        }
        chain.doFilter(request, response);
    }
}

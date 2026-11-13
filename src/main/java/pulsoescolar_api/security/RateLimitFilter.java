package pulsoescolar_api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import pulsoescolar_api.config.RateLimitProperties;

public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Client> clients = new HashMap<>();
    private static final int MAX_CLIENTS = 10_000;

    public RateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || !path(request).startsWith("/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = path(request);
        boolean auth = path.startsWith("/api/auth/") || path.startsWith("/api/invitations/")
                || "POST".equals(request.getMethod()) && path.equals("/api/students");
        var principal = SecurityContextHolder.getContext().getAuthentication();
        String identity;
        if (principal != null && principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken)) {
            identity = "user:" + (principal instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : principal.getName());
        } else {
            identity = "ip:" + request.getRemoteAddr();
        }
        long retry = retryAfter(identity, auth);
        if (retry == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Muitas requisições\",\"status\":429,"
                + "\"detail\":\"Aguarde antes de tentar novamente.\"}");
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private synchronized long retryAfter(String identity, boolean auth) {
        var now = clock.instant();
        var client = clients.get(identity);
        if (client == null) {
            if (clients.size() >= MAX_CLIENTS) {
                clients.values().removeIf(value -> !now.isBefore(value.general.until) && !now.isBefore(value.auth.until));
                if (clients.size() >= MAX_CLIENTS) return Math.max(1,
                        Math.max(properties.window().toSeconds(), properties.authWindow().toSeconds()));
            }
            client = new Client(new Window(now.plus(properties.window())), new Window(now.plus(properties.authWindow())));
            clients.put(identity, client);
        }
        long retry = client.general.consume(now, properties.window(), properties.maxRequests());
        return retry != 0 || !auth ? retry : client.auth.consume(now, properties.authWindow(), properties.authMaxRequests());
    }

    private record Client(Window general, Window auth) {}

    private static class Window {
        Instant until;
        int count;
        Window(Instant until) { this.until = until; }
        long consume(Instant now, Duration duration, int limit) {
            if (!now.isBefore(until)) { count = 0; until = now.plus(duration); }
            if (count >= limit) return Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
            count++;
            return 0;
        }
    }
}

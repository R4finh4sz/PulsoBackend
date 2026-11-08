package pulsoescolar_api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import pulsoescolar_api.config.RateLimitProperties;

public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private static final String DEVICE_COOKIE = "pulso_device";
    // shortcut: limite por instância; usar armazenamento compartilhado ao escalar horizontalmente.
    private final Map<String, Client> clients = new HashMap<>();
    private static final int MAX_CLIENTS = 10_000;

    public RateLimitFilter(RateLimitProperties properties, Clock clock, SecretKey signingKey) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = signingKey;
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
            identity = "device:" + device(request, response);
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

    private String device(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (!DEVICE_COOKIE.equals(cookie.getName())) continue;
                var value = cookie.getValue();
                if (value == null || !value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}")) continue;
                var id = value.substring(0, 36);
                if (MessageDigest.isEqual(signature(id).getBytes(StandardCharsets.US_ASCII),
                        value.substring(37).getBytes(StandardCharsets.US_ASCII))) return id;
            }
        }
        var id = UUID.randomUUID().toString();
        response.addHeader("Set-Cookie", ResponseCookie.from(DEVICE_COOKIE, id + "." + signature(id))
                .httpOnly(true).secure(request.isSecure()).sameSite(request.isSecure() ? "None" : "Lax")
                .path("/api").maxAge(Duration.ofDays(365)).build().toString());
        return id;
    }

    private String signature(String id) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("pulso-rate-limit-device:" + id).getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Assinatura de dispositivo indisponível.", ex);
        }
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

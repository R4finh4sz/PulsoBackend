package pulsoescolar_api.integration.security;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class SecurityAttackIntegrationTest extends HttpIntegrationSupport {
    @ParameterizedTest
    @ValueSource(strings = {"/api/me", "/api/me/export", "/api/admin/metrics", "/actuator/env", "/actuator/heapdump"})
    void unauthenticatedRequestsCannotReadPrivateData(String path) {
        assertThat(request(GET, path, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void studentCannotEscalatePrivilegesOrReadAnotherAccountsExport() throws Exception {
        var studentToken = autenticar(criarUsuario(Role.STUDENT));
        assertThat(request(GET, "/api/admin/metrics", studentToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(POST, "/api/terms", studentToken, Map.of("title", "Injected", "content", "Changed"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var own = request(GET, "/api/me", studentToken, null, Map.class).getBody();
        var exported = request(GET, "/api/me/export?userId=" + teacher.getId(), studentToken, null, String.class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getBody()).doesNotContain(teacher.getEmail()).contains((String) own.get("email"));
    }

    @Test void unsignedJwtCannotImpersonateTeacher() {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var payload = encoder.encodeToString(("{\"sub\":\"" + teacher.getId()
                + "\",\"role\":\"ADMIN\",\"exp\":4102444800}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(request(GET, "/api/admin/metrics", header + "." + payload + ".", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1", "<script>alert(1)</script>", "${jndi:ldap://127.0.0.1/a}"})
    void maliciousPasswordsDoNotBypassAuthentication(String password) {
        var response = request(POST, "/api/auth/login", null,
                Map.of("email", teacher.getEmail(), "password", password), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).doesNotContain("accessToken", "passwordHash", "org.hibernate", "java.lang", password);
    }

    @Test void corsRejectsUntrustedOriginAndAllowsConfiguredOrigin() {
        for (var origin : new String[]{"https://attacker.invalid", "http://localhost:3001"}) {
            var response = client.options().uri("/api/auth/login").header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                    .retrieve().toEntity(String.class);
            if (origin.equals("https://attacker.invalid")) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
            } else {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
            }
        }
    }
}

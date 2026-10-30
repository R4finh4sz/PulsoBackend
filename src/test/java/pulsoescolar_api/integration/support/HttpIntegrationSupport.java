package pulsoescolar_api.integration.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import pulsoescolar_api.dto.auth.LoginResponse;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.service.mail.EmailSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class HttpIntegrationSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired protected ClassroomRepository classrooms;
    @Autowired protected SubjectRepository subjects;
    @MockitoBean EmailSender emailSender;
    protected RestClient client;
    protected String managerToken;
    protected String teacherToken;
    protected SchoolUser teacher;
    private final Map<String, CompletableFuture<String>> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void configurarClienteEUsuarios() throws Exception {
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {}).build();
        doAnswer(invocation -> {
            String text = invocation.getArgument(2);
            var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(text);
            if (matcher.find()) codes.computeIfAbsent(invocation.getArgument(0),
                    key -> new CompletableFuture<>()).complete(matcher.group());
            return null;
        }).when(emailSender).send(anyString(), anyString(), anyString());
        // Bootstrap das identidades; tokens e 2FA passam pelos endpoints reais.
        managerToken = autenticar(criarUsuario(Role.ADMIN));
        teacher = criarUsuario(Role.TEACHER);
        teacherToken = autenticar(teacher);
    }

    private SchoolUser criarUsuario(Role role) {
        var user = new SchoolUser();
        String id = UUID.randomUUID().toString();
        user.setFullName("Teste " + role);
        user.setRa(id);
        user.setEmail(id + "@integration.test");
        user.setPasswordHash(passwords.encode("SenhaTeste123"));
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private String autenticar(SchoolUser user) throws Exception {
        var response = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", user.getEmail(), "password", "SenhaTeste123"))
                .retrieve().toEntity(LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        String token = response.getBody().accessToken();
        assertThat(token).isNotBlank();
        String code = codes.computeIfAbsent(user.getEmail(), key -> new CompletableFuture<>())
                .get(10, TimeUnit.SECONDS);
        var verification = request(HttpMethod.POST, "/api/auth/2fa/verify", token, Map.of("code", code), Void.class);
        assertThat(verification.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return token;
    }

    protected <T> ResponseEntity<T> request(HttpMethod method, String path, String token, Object body, Class<T> type) {
        var request = client.method(method).uri(path);
        if (token != null) request.headers(headers -> headers.setBearerAuth(token));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
        return request.retrieve().toEntity(type);
    }

    protected ClassroomResponse criarSala() {
        var response = request(HttpMethod.POST, "/api/classrooms", managerToken,
                Map.of("name", "Turma " + UUID.randomUUID(), "identifier", "A"), ClassroomResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected ClassroomResponse vincularProfessor(ClassroomResponse room) {
        var response = request(HttpMethod.PUT, "/api/classrooms/" + room.id() + "/teachers/" + teacher.getId(),
                managerToken, null, ClassroomResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().teacherIds()).contains(teacher.getId());
        return response.getBody();
    }

    protected void assertProblem(ResponseEntity<ProblemDetail> response, HttpStatus status, String detail) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getDetail()).contains(detail);
    }
}

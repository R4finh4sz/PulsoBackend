package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authmail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "management.health.mail.enabled=false"})
class AuthMailTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired pulsoescolar_api.repository.school.SchoolRepository schools;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender sender;
    MockMvc mvc;
    Long schoolId;

    @BeforeEach void setup() {
        users.deleteAll();
        schools.deleteAll();
        var school = new pulsoescolar_api.entity.school.School();
        school.setNome("Escola"); school.setCnpj("12345678000190");
        school.setLogradouro("Rua A"); school.setBairro("Centro"); school.setCidade("Recife");
        schoolId = schools.saveAndFlush(school).getId();
        var admin = new SchoolUser();
        admin.setFullName("Admin"); admin.setRa("admin"); admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN); admin.setPasswordHash(encoder.encode("admin-password-123"));
        users.saveAndFlush(admin);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String registration(String name) {
        return "{\"fullName\":\"" + name + "\",\"ra\":\"" + name
                + "\",\"email\":\"" + name + "@example.com\",\"schoolId\":" + schoolId + "}";
    }

    @Test void generatesAndMailsDifferentPasswordsForEveryProfile() throws Exception {
        for (String role : new String[]{"students", "teachers", "coordinators"}) {
            String body = registration(role);
            mvc.perform(post("/api/" + role).with(user("admin@example.com")).with(csrf())
                    .contentType("application/json").content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
        var messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(3)).send(messages.capture());
        var passwords = new java.util.HashSet<String>();
        for (var message : messages.getAllValues()) {
            assertEquals("no-reply@example.com", message.getFrom());
            String password = message.getText().split("Senha: ")[1].split("\n")[0];
            assertEquals(24, password.length());
            passwords.add(password);
            var saved = users.findByEmail(message.getTo()[0]).orElseThrow();
            assertTrue(encoder.matches(password, saved.getPasswordHash()));
        }
        assertEquals(3, passwords.size());
    }

    @Test void smtpFailureRollsBackRegistrationAndHidesTransportDetails() throws Exception {
        doThrow(new MailSendException("secret SMTP details")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/students").with(user("admin@example.com")).with(csrf())
                .contentType("application/json").content(registration("failure")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        assertTrue(users.findByEmail("failure@example.com").isEmpty());
    }

    @Test void duplicateRegistrationDoesNotSendAnotherEmail() throws Exception {
        mvc.perform(post("/api/students").with(user("admin@example.com")).with(csrf())
                .contentType("application/json").content(registration("same"))).andExpect(status().isCreated());
        mvc.perform(post("/api/students").with(user("admin@example.com")).with(csrf())
                .contentType("application/json").content(registration("same"))).andExpect(status().isConflict());
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void loginUsesMailedPasswordPersistsSessionRotatesCsrfAndLogsOut() throws Exception {
        mvc.perform(post("/api/students").with(user("admin@example.com")).with(csrf())
                .contentType("application/json").content(registration("newstudent"))).andExpect(status().isCreated());
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        String password = message.getValue().getText().split("Senha: ")[1].split("\n")[0];
        var csrfResult = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var token = (CsrfToken) csrfResult.getRequest().getAttribute(CsrfToken.class.getName());
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String oldSessionId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).header(token.getHeaderName(), token.getToken())
                .contentType("application/json")
                .content("{\"email\":\"NEWSTUDENT@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertNotEquals(oldSessionId, session.getId());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newstudent@example.com"));
        mvc.perform(get("/api/students").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header(token.getHeaderName(), token.getToken()))
                .andExpect(status().isForbidden());
        var fresh = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var freshToken = (CsrfToken) fresh.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/api/auth/logout").session(session)
                .header(freshToken.getHeaderName(), freshToken.getToken())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidLoginAndRequiresCsrfWithoutLeakingPasswords() throws Exception {
        String body = "{\"email\":\"admin@example.com\",\"password\":\"wrong-password\"}";
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(body.replace("admin@example.com", "missing@example.com")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertFalse(new pulsoescolar_api.dto.auth.LoginRequest("admin@example.com", "secret")
                .toString().contains("secret"));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}

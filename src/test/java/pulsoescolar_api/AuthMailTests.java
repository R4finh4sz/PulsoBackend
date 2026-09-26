package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;


import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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
            mvc.perform(post("/api/" + role).with(user("admin@example.com").roles("ADMIN"))
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
        mvc.perform(post("/api/students").with(user("admin@example.com").roles("ADMIN"))
                .contentType("application/json").content(registration("failure")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        assertTrue(users.findByEmail("failure@example.com").isEmpty());
    }

    @Test void duplicateRegistrationDoesNotSendAnotherEmail() throws Exception {
        mvc.perform(post("/api/students").with(user("admin@example.com").roles("ADMIN"))
                .contentType("application/json").content(registration("same"))).andExpect(status().isCreated());
        mvc.perform(post("/api/students").with(user("admin@example.com").roles("ADMIN"))
                .contentType("application/json").content(registration("same"))).andExpect(status().isConflict());
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void loginUsesMailedPasswordAndReturnsJwt() throws Exception {
        mvc.perform(post("/api/students").with(user("admin@example.com").roles("ADMIN"))
                .contentType("application/json").content(registration("newstudent")))
                .andExpect(status().isCreated());
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(3000)).send(message.capture());
        String password = message.getValue().getText().split("Senha: ")[1].split("\n")[0];
        org.mockito.Mockito.clearInvocations(sender);
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"NEWSTUDENT@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.termsAccepted").value(false))
                .andExpect(jsonPath("$.user.schoolId").value(schoolId))
                .andExpect(jsonPath("$.user.classroomId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        String token = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verify(sender, timeout(3000)).send(message.capture());
        String code = message.getValue().getText().split("\n")[0].replaceAll("[^0-9]", "");
        mvc.perform(post("/api/auth/2fa/verify").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        assertTrue(encoder.matches(password, users.findByEmail("newstudent@example.com").orElseThrow().getPasswordHash()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("newstudent@example.com"));
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}

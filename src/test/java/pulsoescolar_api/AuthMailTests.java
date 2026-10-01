package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.core.exception.ResendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.repository.terms.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authmail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.resend.api-key=re_test_fake_key"})
class AuthMailTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RegistrationRepository requests;
    @Autowired UserInvitationRepository invitations;
    @Autowired TermsRepository terms;
    @Autowired TermsVersionRepository versions;
    @Autowired pulsoescolar_api.repository.school.SchoolRepository schools;
    @Autowired PasswordEncoder encoder;
    @MockitoBean Emails sender;
    MockMvc mvc;
    Long schoolId;

    @BeforeEach void setup() {
        invitations.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        schools.deleteAll();
        var school = new pulsoescolar_api.entity.school.School();
        school.setNome("Escola"); school.setCnpj("12345678000190");
        school.setLogradouro("Rua A"); school.setBairro("Centro"); school.setCidade("Recife");
        schoolId = schools.saveAndFlush(school).getId();
        var coordinator = new SchoolUser();
        coordinator.setFullName("Coordinator"); coordinator.setRa("coordinator"); coordinator.setEmail("coordinator@example.com");
        coordinator.setRole(Role.PEDAGOGICAL_COORDINATOR); coordinator.setSchool(school);
        coordinator.setPasswordHash(encoder.encode("coordinator-password-123")); users.saveAndFlush(coordinator);
        var term = terms.findById(1L).orElseThrow();
        term.setVersion(1); term.setTitle("Terms"); term.setContent("Published terms"); terms.saveAndFlush(term);
        if (!versions.existsById(1L)) versions.saveAndFlush(new pulsoescolar_api.entity.terms.TermsVersion(1L, "Terms", "Published terms"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String registration(String name) {
        return """
                {"name":"%s User","birthDate":"2000-01-01","ra":"%d","email":"%s@example.com",
                 "schoolId":%d,"password":"chosen-password-123","termsAccepted":true,"termsVersion":"1.0"}
                """.formatted(name, Math.abs((long) name.hashCode()), name, schoolId);
    }

    @Test void studentUsesChosenPasswordAndDoesNotEmailIt() throws Exception {
        mvc.perform(post("/api/students").contentType("application/json").content(registration("students")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verifyNoInteractions(sender);
        for (var request : requests.findAll()) assertTrue(encoder.matches("chosen-password-123", request.getPasswordHash()));
    }

    @Test void providerFailureDoesNotPreventSubmittingRegistration() throws Exception {
        doThrow(new ResendException("secret provider details")).when(sender).send(any(CreateEmailOptions.class));
        mvc.perform(post("/api/students").contentType("application/json").content(registration("failure")))
                .andExpect(status().isCreated());
        assertTrue(users.findByEmail("failure@example.com").isEmpty());
        verifyNoInteractions(sender);
    }

    @Test void duplicateRegistrationDoesNotSendEmail() throws Exception {
        mvc.perform(post("/api/students").contentType("application/json").content(registration("same"))).andExpect(status().isCreated());
        mvc.perform(post("/api/students").contentType("application/json").content(registration("same"))).andExpect(status().isConflict());
        verifyNoInteractions(sender);
    }

    @Test void resendFailureRollsBackInvitationAndReturnsSafeError() throws Exception {
        doThrow(new ResendException(403, "{\"message\":\"secret provider details\"}"))
                .when(sender).send(any(CreateEmailOptions.class));
        var result = mvc.perform(post("/api/invitations/teachers")
                        .with(user("coordinator@example.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"email\":\"invited@example.com\"}"))
                .andExpect(status().isServiceUnavailable()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("secret provider details"));
        assertEquals(0, invitations.count());
        verify(sender).send(any(CreateEmailOptions.class));
    }

    @Test void approvedUserLogsInWithChosenPasswordAndStillRequiresEmailTwoFactor() throws Exception {
        mvc.perform(post("/api/students").contentType("application/json").content(registration("newstudent")))
                .andExpect(status().isCreated());
        String login = "{\"email\":\"NEWSTUDENT@example.com\",\"password\":\"chosen-password-123\"}";
        mvc.perform(post("/api/auth/login").contentType("application/json").content(login)).andExpect(status().isUnauthorized());
        verifyNoInteractions(sender);
        long requestId = requests.findAll().getFirst().getId();
        mvc.perform(patch("/api/registration-requests/" + requestId)
                        .with(user("coordinator@example.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        verifyNoInteractions(sender);
        var result = mvc.perform(post("/api/auth/login").contentType("application/json").content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.termsAccepted").value(true))
                .andExpect(jsonPath("$.user.schoolId").value(schoolId))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        String token = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        var message = org.mockito.ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(sender, timeout(3000)).send(message.capture());
        assertFalse(message.getValue().getText().contains("chosen-password-123"));
        String code = message.getValue().getText().split("\n")[0].replaceAll("[^0-9]", "");
        mvc.perform(post("/api/auth/2fa/verify").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("newstudent@example.com"));
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}

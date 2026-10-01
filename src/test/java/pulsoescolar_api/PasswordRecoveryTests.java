package pulsoescolar_api;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.auth.*;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.service.mail.EmailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PasswordRecoveryTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RegistrationRepository registrations;
    @Autowired SchoolRepository schools;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired AuthSessionRepository sessions;
    @Autowired pulsoescolar_api.repository.terms.TermsRepository terms;
    @Autowired pulsoescolar_api.repository.terms.TermsVersionRepository versions;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @MockitoBean EmailSender sender;
    MockMvc mvc;
    SchoolUser account;

    @BeforeEach void setup() {
        sessions.deleteAll(); recoveries.deleteAll(); registrations.deleteAll(); users.deleteAll(); schools.deleteAll();
        account = new SchoolUser();
        account.setFullName("Nome que não deve aparecer"); account.setRa("recovery-user");
        account.setEmail("person@example.com"); account.setRole(Role.STUDENT);
        account.setPasswordHash(passwords.encode("Original123")); users.saveAndFlush(account);
        var term = terms.findById(1L).orElseThrow();
        term.setVersion(1); term.setTitle("Termos"); term.setContent("Conteúdo"); terms.saveAndFlush(term);
        if (!versions.existsById(1L)) versions.saveAndFlush(new pulsoescolar_api.entity.terms.TermsVersion(1L, "Termos", "Conteúdo"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String requestCode() throws Exception {
        mvc.perform(post("/api/auth/password-recovery/request").contentType("application/json")
                .content("{\"email\":\"PERSON@EXAMPLE.COM\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"));
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender, timeout(3000)).send(eq(account.getEmail()), contains("Código para redefinir"), capture.capture());
        String text = capture.getValue();
        assertTrue(text.endsWith("Equipe PulsoEscolar"));
        assertFalse(text.contains(account.getFullName())); assertFalse(text.contains("http"));
        var matcher = Pattern.compile("[0-9]{6}").matcher(text);
        assertTrue(matcher.find()); return matcher.group();
    }

    String verifyCode(String code) throws Exception {
        var response = mvc.perform(post("/api/auth/password-recovery/verify").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", code))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("resetToken").asString();
    }

    String resetBody(String token, String password, String confirmation) {
        return json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "resetToken", token,
                "newPassword", password, "confirmPassword", confirmation));
    }

    @Test void anonymousFlowRequiresVerificationChangesPasswordAndRevokesSessions() throws Exception {
        var session = new AuthSession(); session.setId(UUID.randomUUID()); session.setUser(account);
        session.setCreatedAt(Instant.now()); session.setExpiresAt(Instant.now().plusSeconds(3600));
        sessions.saveAndFlush(session);
        String code = requestCode();
        mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                .content(resetBody("A".repeat(43), "Changed123", "Changed123"))).andExpect(status().isBadRequest());
        String token = verifyCode(code);
        mvc.perform(post("/api/auth/password-recovery/verify").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", code))))
                .andExpect(status().isBadRequest());
        assertFalse(recoveries.findById(account.getId()).orElseThrow().getResetTokenHash().contains(token));
        mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                .content(resetBody(token, "Changed123", "Changed123"))).andExpect(status().isNoContent());
        assertTrue(passwords.matches("Changed123", users.findById(account.getId()).orElseThrow().getPasswordHash()));
        assertEquals(0, sessions.count());
        verify(sender, timeout(3000)).send(eq(account.getEmail()), contains("foi alterada"), contains("Equipe PulsoEscolar"));
        mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                .content(resetBody(token, "Another123", "Another123"))).andExpect(status().isBadRequest());
    }

    @Test void wrongAttemptsPersistAndAreLimitedWithoutResendBypassingCooldown() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/password-recovery/verify").contentType("application/json")
                    .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", wrong))))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(5, recoveries.findById(account.getId()).orElseThrow().getCodeAttempts());
        mvc.perform(post("/api/auth/password-recovery/request").contentType("application/json")
                .content("{\"email\":\"person@example.com\"}")).andExpect(status().isAccepted());
        assertEquals(5, recoveries.findById(account.getId()).orElseThrow().getCodeAttempts());
        mvc.perform(post("/api/auth/password-recovery/verify").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", code))))
                .andExpect(status().isBadRequest());
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test void expiredCodeAndExpiredResetTokenCannotChangePassword() throws Exception {
        String code = requestCode();
        var recovery = recoveries.findById(account.getId()).orElseThrow();
        recovery.setCodeExpiresAt(Instant.now().minusSeconds(1)); recoveries.saveAndFlush(recovery);
        mvc.perform(post("/api/auth/password-recovery/verify").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "code", code))))
                .andExpect(status().isBadRequest());
        recovery.setCodeExpiresAt(Instant.now().plusSeconds(600)); recoveries.saveAndFlush(recovery);
        String token = verifyCode(code);
        recovery = recoveries.findById(account.getId()).orElseThrow();
        recovery.setResetExpiresAt(Instant.now().minusSeconds(1)); recoveries.saveAndFlush(recovery);
        mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                .content(resetBody(token, "Changed123", "Changed123"))).andExpect(status().isBadRequest());
        assertTrue(passwords.matches("Original123", users.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void validatesConfirmationAndPasswordPolicyWithoutConsumingValidToken() throws Exception {
        String token = verifyCode(requestCode());
        for (String[] values : new String[][]{{"Changed123", "Other123"}, {"weak", "weak"}, {"Original123", "Original123"}}) {
            mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                    .content(resetBody(token, values[0], values[1]))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/password-recovery/reset").contentType("application/json")
                .content(resetBody(token, "Changed123", "Changed123"))).andExpect(status().isNoContent());
    }

    @Test void unknownAndDeletedAccountsReceiveSamePublicResponseWithoutEmail() throws Exception {
        var absent = mvc.perform(post("/api/auth/password-recovery/request").contentType("application/json")
                .content("{\"email\":\"absent@example.com\"}")).andExpect(status().isAccepted()).andReturn();
        account.setDeletedAt(Instant.now()); users.saveAndFlush(account);
        var deleted = mvc.perform(post("/api/auth/password-recovery/request").contentType("application/json")
                .content("{\"email\":\"person@example.com\"}")).andExpect(status().isAccepted()).andReturn();
        assertEquals(absent.getResponse().getContentAsString(), deleted.getResponse().getContentAsString());
        verifyNoInteractions(sender); assertEquals(0, recoveries.count());
    }

    @ParameterizedTest
    @CsvSource({"STUDENT,APPROVED,aluno", "TEACHER,APPROVED,professor", "PEDAGOGICAL_COORDINATOR,APPROVED,coordenador",
                "STUDENT,REJECTED,aluno", "TEACHER,REJECTED,professor", "PEDAGOGICAL_COORDINATOR,REJECTED,coordenador"})
    void reviewEmailsMatchProfileAndDecisionWithoutNamesOrLinks(Role role, RegistrationStatus status, String profile) throws Exception {
        var school = new School(); school.setNome("Escola"); school.setCnpj("12345678000190");
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Recife"); schools.saveAndFlush(school);
        Role actorRole = role == Role.PEDAGOGICAL_COORDINATOR ? Role.ADMIN : Role.PEDAGOGICAL_COORDINATOR;
        account.setRole(actorRole); account.setSchool(actorRole == Role.ADMIN ? null : school); users.saveAndFlush(account);
        var request = new RegistrationRequest(); request.setFullName("Pessoa que não aparece no e-mail");
        request.setEmail("reviewed@example.com"); request.setRa("reviewed"); request.setPasswordHash(passwords.encode("Chosen123"));
        request.setRole(role); request.setSchool(school); request.setTermsVersion(1L);
        request.setStatus(RegistrationStatus.PENDING); request.setRequestedAt(Instant.now()); registrations.saveAndFlush(request);
        String body = json.writeValueAsString(java.util.Map.of("status", status.name(), "reason", "Documentação pendente"));
        mvc.perform(patch("/api/registration-requests/" + request.getId()).with(user(account.getEmail()).roles(actorRole.name()))
                .contentType("application/json").content(body)).andExpect(status().isOk());
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender, timeout(3000)).send(eq(request.getEmail()), anyString(), capture.capture());
        String text = capture.getValue(); assertTrue(text.contains("como " + profile));
        assertTrue(text.endsWith("Equipe PulsoEscolar")); assertFalse(text.contains(request.getFullName())); assertFalse(text.contains("http"));
        if (status == RegistrationStatus.APPROVED) assertTrue(text.contains("Parabéns!"));
        else assertTrue(text.contains("Motivo: Documentação pendente"));
        mvc.perform(patch("/api/registration-requests/" + request.getId()).with(user(account.getEmail()).roles(actorRole.name()))
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }
}

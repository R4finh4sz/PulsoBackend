package pulsoescolar_api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.*;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.service.mail.InvitationMailService;
import pulsoescolar_api.service.user.InvitationTokenService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: failed HTTP requests must persist their attempt counters.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invitations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class InvitationTests {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    @Autowired WebApplicationContext context;
    @Autowired UserInvitationRepository invitations;
    @Autowired RegistrationRepository registrations;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired TermsRepository terms;
    @Autowired TermsVersionRepository versions;
    @Autowired TransactionTemplate transactions;
    @Autowired InvitationTokenService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @MockitoBean InvitationMailService mail;
    @MockitoBean Clock clock;
    MockMvc mvc;
    Long schoolId;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        invitations.deleteAll();
        registrations.deleteAll();
        users.deleteAll();
        schools.deleteAll();
        var school = new School();
        school.setNome("Escola"); school.setCnpj("12345678000190");
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Recife");
        schoolId = schools.saveAndFlush(school).getId();
        actor("admin", Role.ADMIN, null);
        actor("coordinator", Role.PEDAGOGICAL_COORDINATOR, school);
        actor("teacher", Role.TEACHER, school);
        transactions.executeWithoutResult(status -> {
            var term = terms.lockCurrent();
            term.setVersion(1); term.setTitle("Termos"); term.setContent("Conteúdo");
        });
        if (!versions.existsById(1L)) versions.saveAndFlush(new TermsVersion(1L, "Termos", "Conteúdo"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void actor(String name, Role role, School school) {
        var actor = new SchoolUser();
        actor.setFullName(name); actor.setRa(name); actor.setEmail(name + "@example.com");
        actor.setPasswordHash("unused"); actor.setRole(role); actor.setSchool(school);
        users.saveAndFlush(actor);
    }

    private String create(boolean coordinator) throws Exception {
        String body = coordinator ? "{\"email\":\"invited@example.com\",\"schoolId\":" + schoolId + "}"
                : "{\"email\":\"invited@example.com\"}";
        var result = mvc.perform(post("/api/invitations/" + (coordinator ? "coordinators" : "teachers"))
                        .with(user((coordinator ? "admin" : "coordinator") + "@example.com")
                                .roles(coordinator ? "ADMIN" : "PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String open(String token) throws Exception {
        mvc.perform(get("/api/invitations/" + token)).andExpect(status().isOk());
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendCode(eq("invited@example.com"), code.capture());
        return code.getValue();
    }

    private void verifyCode(String token, String code, int status) throws Exception {
        mvc.perform(post("/api/invitations/" + token + "/verify").contentType("application/json")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().is(status));
    }

    private String completion() {
        return """
                {"name":"Pessoa Teste","ra":"1234","password":"Chosen-password-123",
                 "termsAccepted":true,"termsVersion":"1.0"}
                """;
    }

    @Test void wrongAttemptsPersistAndOpeningCannotResetOrResend() throws Exception {
        String token = create(false);
        String code = open(token);
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) verifyCode(token, wrong, 400);
        assertEquals(5, invitations.findAll().getFirst().getVerificationAttempts());
        mvc.perform(get("/api/invitations/" + token)).andExpect(status().isOk());
        verify(mail, times(1)).sendCode(anyString(), anyString());
        verifyCode(token, code, 400);
        mvc.perform(post("/api/invitations/" + token + "/resend")).andExpect(status().isTooManyRequests());
        when(clock.instant()).thenReturn(NOW.plusSeconds(180));
        mvc.perform(post("/api/invitations/" + token + "/resend")).andExpect(status().isOk());
        assertEquals(0, invitations.findAll().getFirst().getVerificationAttempts());
        var codes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail, times(2)).sendCode(anyString(), codes.capture());
        verifyCode(token, codes.getAllValues().getLast(), 200);
        verifyCode(token, code, 409);
        mvc.perform(post("/api/invitations/" + token + "/resend")).andExpect(status().isConflict());
    }

    @Test void codeExpiresAndReopeningDoesNotIssueAnother() throws Exception {
        String token = create(false);
        String code = open(token);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        verifyCode(token, code, 400);
        mvc.perform(get("/api/invitations/" + token)).andExpect(status().isOk());
        verify(mail, times(1)).sendCode(anyString(), anyString());
        mvc.perform(post("/api/invitations/" + token + "/resend")).andExpect(status().isOk());
    }

    @Test void expiredInvitationAllowsReplacementButLiveInvitationDoesNot() throws Exception {
        String token = create(false);
        mvc.perform(post("/api/invitations/teachers")
                        .with(user("coordinator@example.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"email\":\"INVITED@example.com\"}"))
                .andExpect(status().isConflict());
        when(clock.instant()).thenReturn(NOW.plusSeconds(172800));
        mvc.perform(get("/api/invitations/" + token)).andExpect(status().isGone());
        assertNotEquals(token, create(false));
        assertEquals(2, invitations.count());
    }

    @Test void completionRequiresVerificationAndExplicitCurrentTerms() throws Exception {
        String token = create(false);
        String endpoint = "/api/invitations/" + token + "/complete";
        mvc.perform(post(endpoint).contentType("application/json").content(completion())).andExpect(status().isForbidden());
        verifyCode(token, open(token), 200);
        for (String body : new String[]{completion().replace("true", "false"),
                completion().replace("true", "null"), completion().replace("\"termsAccepted\":true,", ""),
                completion().replace(",\"termsVersion\":\"1.0\"", "")}) {
            mvc.perform(post(endpoint).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        transactions.executeWithoutResult(status -> {
            versions.saveAndFlush(new TermsVersion(2L, "Novos termos", "Novo conteúdo"));
            terms.lockCurrent().setVersion(2);
        });
        mvc.perform(post(endpoint).contentType("application/json").content(completion())).andExpect(status().isConflict());
        assertEquals(0, registrations.count());
        assertNull(invitations.findAll().getFirst().getUsedAt());
        mvc.perform(post(endpoint).contentType("application/json").content(completion().replace("1.0", "1.1")))
                .andExpect(status().isCreated());
        assertEquals(2L, registrations.findAll().getFirst().getTermsVersion());
    }

    @Test void teacherInvitationPreservesPasswordRoleSchoolAndTermsThroughApproval() throws Exception {
        completeAndApprove(false);
    }

    @Test void coordinatorInvitationPreservesPasswordRoleSchoolAndTermsThroughApproval() throws Exception {
        completeAndApprove(true);
    }

    private void completeAndApprove(boolean coordinator) throws Exception {
        String token = create(coordinator);
        assertEquals(tokens.hash(token), invitations.findAll().getFirst().getTokenHash());
        verifyCode(token, open(token), 200);
        var response = mvc.perform(post("/api/invitations/" + token + "/complete")
                        .contentType("application/json").content(completion()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        assertTrue(passwords.matches("Chosen-password-123", registrations.findById(id).orElseThrow().getPasswordHash()));
        mvc.perform(post("/api/invitations/" + token + "/complete").contentType("application/json").content(completion()))
                .andExpect(status().isGone());
        mvc.perform(patch("/api/registration-requests/" + id)
                        .with(user((coordinator ? "admin" : "coordinator") + "@example.com")
                                .roles(coordinator ? "ADMIN" : "PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        transactions.executeWithoutResult(status -> {
            var account = users.findByEmail("invited@example.com").orElseThrow();
            assertEquals(coordinator ? Role.PEDAGOGICAL_COORDINATOR : Role.TEACHER, account.getRole());
            assertEquals(schoolId, account.getSchool().getId());
            assertTrue(account.isTermsAccepted());
            assertEquals(java.util.Set.of(1L), account.getAcceptedTermVersions());
            assertTrue(passwords.matches("Chosen-password-123", account.getPasswordHash()));
        });
        verify(mail, times(1)).sendInvitation(eq("invited@example.com"), eq(token));
        verify(mail, times(1)).sendCode(eq("invited@example.com"), anyString());
        verifyNoMoreInteractions(mail);
    }

    @Test void authorizationAndMissingCodeAreRejected() throws Exception {
        mvc.perform(post("/api/invitations/teachers").contentType("application/json")
                        .content("{\"email\":\"invited@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/invitations/teachers").with(user("teacher@example.com").roles("TEACHER"))
                        .contentType("application/json").content("{\"email\":\"invited@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invitations/coordinators")
                        .with(user("coordinator@example.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"email\":\"invited@example.com\",\"schoolId\":" + schoolId + "}"))
                .andExpect(status().isForbidden());
        String token = create(false);
        open(token);
        for (String body : new String[]{"{}", "{\"code\":null}", "{\"code\":\"\"}"}) {
            mvc.perform(post("/api/invitations/" + token + "/verify").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, invitations.findAll().getFirst().getVerificationAttempts());
    }

    @Test void sensitiveDtosHideCredentials() {
        assertFalse(new CompleteInvitationRequest("Pessoa Teste", "1234", "Secret123", true, "1.0").toString().contains("Secret123"));
        assertFalse(new VerifyInvitationRequest("123456").toString().contains("123456"));
        assertFalse(new InvitationReceipt("secret-token", "person@example.com", Role.TEACHER, 1L, NOW)
                .toString().contains("secret-token"));
    }
}

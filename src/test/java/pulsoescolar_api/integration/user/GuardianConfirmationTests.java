package pulsoescolar_api.integration.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import pulsoescolar_api.entity.guardian.GuardianStatus;
import pulsoescolar_api.config.JwtConfig;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.entity.legal.LegalDocument;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.guardian.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.*;
import pulsoescolar_api.repository.user.*;
import pulsoescolar_api.service.mail.NotificationMailRequested;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional @RecordApplicationEvents
class GuardianConfirmationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired RegistrationRepository requests;
    @Autowired GuardianConfirmationRepository confirmations;
    @Autowired GuardianInvitationRepository invitations;
    @Autowired TermsRepository terms;
    @Autowired TermsVersionRepository versions;
    @Autowired JsonMapper json;
    @Autowired ApplicationEvents events;
    @Autowired AuthSessionRepository sessions;
    @Autowired JwtEncoder encoder;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    pulsoescolar_api.service.school.SchoolAddressValidator addressValidator;
    MockMvc mvc;
    School school, other;
    static final Pattern LINK = Pattern.compile("https://\\S+/([A-Za-z0-9_-]+)");

    School school(String cep, String uf) {
        var s = new School(); s.setNome("Escola " + uf); s.setLogradouro("Rua");
        s.setBairro("Centro"); s.setCidade("Recife"); s.setCep(cep); s.setUf(uf);
        return schools.saveAndFlush(s);
    }
    SchoolUser actor(String name, Role role, School school) {
        var u = new SchoolUser(); u.setFullName(name); u.setRa(name); u.setEmail(name + "@test.com");
        u.setPasswordHash("unused"); u.setRole(role); u.setSchool(school); return users.saveAndFlush(u);
    }
    @Autowired pulsoescolar_api.repository.agreement.SchoolDataAgreementRepository agreements;
    @Autowired pulsoescolar_api.repository.legal.LegalDocumentRepository legalDocs;
    @org.junit.jupiter.api.AfterEach void cleanAgreements() { agreements.deleteAll(); }
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        school = school("50000000", "PE");
        other = school("60000000", "SP");
        agreements.deleteAll();
        var admin = actor("admin", Role.ADMIN, null);
        pulsoescolar_api.integration.support.AgreementFixture.publish(legalDocs,
                pulsoescolar_api.entity.legal.LegalDocumentType.STUDENT_TERM, admin);
        pulsoescolar_api.integration.support.AgreementFixture.publish(legalDocs,
                pulsoescolar_api.entity.legal.LegalDocumentType.PRIVACY_POLICY, admin);
        pulsoescolar_api.integration.support.AgreementFixture.publish(legalDocs,
                pulsoescolar_api.entity.legal.LegalDocumentType.GUARDIAN_TERM, admin);
        pulsoescolar_api.integration.support.AgreementFixture.accept(legalDocs, agreements, school, actor("coordinator", Role.PEDAGOGICAL_COORDINATOR, school));
        pulsoescolar_api.integration.support.AgreementFixture.accept(legalDocs, agreements, other, actor("outsider", Role.PEDAGOGICAL_COORDINATOR, other));
        var t = terms.lockCurrent(); t.setVersion(1); t.setTitle("Termos"); t.setContent("Conteúdo"); terms.saveAndFlush(t);
        versions.saveAndFlush(new TermsVersion(1L, "Termos", "Conteúdo"));
    }

    String payload(String ra, LocalDate birth, String guardian) {
        return """
                {"name":"Aluno Menor","birthDate":"%s","ra":"%s","email":"aluno%s@example.com",
                 "password":"Chosen-password-123","confirmPassword":"Chosen-password-123","schoolId":%d,
                 "termsAccepted":true,"termsVersion":"1.0","privacyAccepted":true,"privacyVersion":"1.0","role":"STUDENT"%s}
                """.formatted(birth, ra, ra, school.getId(), guardian);
    }
    static final String GUARDIAN = ",\"guardianName\":\"Maria Responsavel\",\"guardianEmail\":\"maria@example.com\"";

    long register(String ra) throws Exception {
        var r = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(payload(ra, LocalDate.now().minusYears(16), GUARDIAN)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }
    long confirmationId(long registrationId) {
        return confirmations.findByRegistrationId(registrationId).orElseThrow().getId();
    }
    GuardianStatus statusOf(long id) { return confirmations.findById(id).orElseThrow().getStatus(); }
    void verify(long id, String actor, int expected) throws Exception {
        mvc.perform(post("/api/guardian-confirmations/" + id + "/verify")
                        .with(user(actor + "@test.com").roles(actor.equals("admin") ? "ADMIN" : "PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json")
                        .content("{\"method\":\"Registro escolar\",\"justification\":\"Suporte\"}"))
                .andExpect(status().is(expected));
    }
    NotificationMailRequested lastMail() {
        return events.stream(NotificationMailRequested.class).reduce((a, b) -> b).orElseThrow();
    }
    String token(NotificationMailRequested mail) {
        var matcher = LINK.matcher(mail.text());
        String token = null;
        while (matcher.find()) token = matcher.group(1);
        assertNotNull(token, "link not found in e-mail");
        return token;
    }
    void decide(String token, String body, int expected) throws Exception {
        mvc.perform(post("/api/guardian-invitations/" + token + "/decision")
                .contentType("application/json").content(body)).andExpect(status().is(expected));
    }
    static final String CONFIRM = "{\"decision\":\"CONFIRM\",\"relationship\":\"MOTHER\",\"declaration\":true,\"termsVersion\":\"1.0\",\"privacyAccepted\":true,\"privacyVersion\":\"1.0\"}";

    @Test void minorMustInformGuardianAndAdultDoesNotNeedOne() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(payload("100", LocalDate.now().minusYears(16), ""))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(payload("101", LocalDate.now().minusYears(16),
                        ",\"guardianName\":\"Mae\",\"guardianEmail\":\"aluno101@example.com\""))).andExpect(status().isBadRequest());
        assertTrue(requests.findAll().stream().noneMatch(r -> r.getRa().equals("100") || r.getRa().equals("101")));
        long minor = register("102");
        assertEquals(GuardianStatus.AWAITING_SCHOOL_CHECK, statusOf(confirmationId(minor)));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(payload("103", LocalDate.now().minusYears(19), ""))).andExpect(status().isCreated());
        assertTrue(confirmations.findByRegistrationId(requests.findAll().stream()
                .filter(r -> r.getRa().equals("103")).findFirst().orElseThrow().getId()).isEmpty());
        assertEquals(0, events.stream(NotificationMailRequested.class).count(), "nothing is sent before the school check");
    }

    @Test void minorWithoutGuardianRecordCannotBeApproved() throws Exception {
        long registration = register("109");
        confirmations.delete(confirmations.findByRegistrationId(registration).orElseThrow());
        confirmations.flush();
        review(registration, "coordinator", "APPROVED", 409);
    }

    @Test void schoolCheckSendsInvitationAndGuardianConfirmsOnceWithMinimumData() throws Exception {
        long registration = register("110");
        long id = confirmationId(registration);
        mvc.perform(get("/api/guardian-confirmations").with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].guardianEmail").value("maria@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("AWAITING_SCHOOL_CHECK"));
        review(registration, "coordinator", "APPROVED", 409);

        verify(id, "coordinator", 200);
        var mail = lastMail();
        assertEquals("maria@example.com", mail.email());
        var token = token(mail);

        mvc.perform(get("/api/guardian-invitations/" + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.studentName").value("Aluno Menor"))
                .andExpect(jsonPath("$.schoolName").value("Escola PE"))
                .andExpect(jsonPath("$.terms.version").value("1.0"))
                .andExpect(jsonPath("$.ra").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist());
        assertEquals(GuardianStatus.AWAITING_GUARDIAN, statusOf(id), "opening the link is not a decision");

        decide(token, "{\"decision\":\"CONFIRM\",\"relationship\":\"MOTHER\",\"declaration\":false,\"termsVersion\":\"1.0\"}", 400);
        decide(token, "{\"decision\":\"CONFIRM\",\"relationship\":\"MOTHER\",\"declaration\":true,\"termsVersion\":\"9.9\"}", 400);
        assertEquals(GuardianStatus.AWAITING_GUARDIAN, statusOf(id));
        decide(token, CONFIRM.replace("\"privacyAccepted\":true", "\"privacyAccepted\":false"), 400);
        decide(token, CONFIRM, 200);
        assertEquals(GuardianStatus.CONFIRMED, statusOf(id));
        var invitation = invitations.findFirstByConfirmationIdOrderByIdDesc(id).orElseThrow();
        assertEquals(1L, invitation.getTermsVersion());
        assertNotNull(invitation.getDecidedAt());
        decide(token, CONFIRM, 410);
        mvc.perform(get("/api/guardian-invitations/" + token)).andExpect(status().isGone());

        review(registration, "coordinator", "APPROVED", 200);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = LegalDocumentType.class, names = {"GUARDIAN_TERM", "PRIVACY_POLICY"})
    void updatedDocumentsBlockStudentUntilGuardianRenews(LegalDocumentType updated) throws Exception {
        long registration = register("111");
        long id = confirmationId(registration);
        verify(id, "coordinator", 200);
        decide(token(lastMail()), CONFIRM, 200);
        review(registration, "coordinator", "APPROVED", 200);
        var student = requests.findById(registration).orElseThrow().getUser();

        var now = Instant.now();
        var session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUser(student);
        session.setTwoFactorVerified(true);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        sessions.saveAndFlush(session);
        var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(student.getId().toString()).id(session.getId().toString())
                .issuedAt(now).expiresAt(session.getExpiresAt()).build();
        var bearer = "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        var renewal = "/api/me/guardian-renewal";
        mvc.perform(get(renewal).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(false));
        student.setTermsAccepted(false);
        mvc.perform(get(renewal).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(false));
        student.setTermsAccepted(true);

        legalDocs.saveAndFlush(new LegalDocument(updated, 2, "Documento atualizado", "Novo conteúdo",
                "hash-novo", now, users.findByEmail("admin@test.com").orElseThrow()));
        mvc.perform(get("/api/me/export").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/search").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get(renewal).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sendsRemaining").value(3));
        String latestToken = null;
        for (int remaining = 2; remaining >= 0; remaining--) {
            mvc.perform(post(renewal + "/send").header("Authorization", bearer))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.sendsRemaining").value(remaining));
            latestToken = token(lastMail());
            if (remaining == 2) {
                mvc.perform(post(renewal + "/send").header("Authorization", bearer))
                        .andExpect(status().isTooManyRequests());
            }
            var invitation = invitations.findFirstByConfirmationIdOrderByIdDesc(id).orElseThrow();
            invitation.setCreatedAt(now.minusSeconds(181));
            invitations.saveAndFlush(invitation);
        }
        mvc.perform(post(renewal + "/send").header("Authorization", bearer)).andExpect(status().isConflict());
        decide(latestToken, CONFIRM, updated == LegalDocumentType.GUARDIAN_TERM ? 400 : 409);
        var versionField = updated == LegalDocumentType.GUARDIAN_TERM ? "termsVersion" : "privacyVersion";
        decide(latestToken, CONFIRM.replace("\"" + versionField + "\":\"1.0\"", "\"" + versionField + "\":\"1.1\""), 200);
        mvc.perform(get(renewal).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(false));
    }

    @Test void renewalRoutesRejectAnonymousAndNonStudents() throws Exception {
        var renewal = "/api/me/guardian-renewal";
        mvc.perform(get(renewal)).andExpect(status().isUnauthorized());
        mvc.perform(post(renewal + "/send")).andExpect(status().isUnauthorized());
        for (var authority : List.of("TERMS_PENDING", "ROLE_TEACHER", "ROLE_PEDAGOGICAL_COORDINATOR", "ROLE_ADMIN")) {
            var actor = user("other@test.com").authorities(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(authority));
            mvc.perform(get(renewal).with(actor)).andExpect(status().isForbidden());
            mvc.perform(post(renewal + "/send").with(actor)).andExpect(status().isForbidden());
        }
    }

    void review(long id, String actor, String decision, int expected) throws Exception {
        mvc.perform(patch("/api/registration-requests/" + id)
                        .with(user(actor + "@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json")
                        .content("{\"status\":\"" + decision + "\",\"identityChecked\":true}"))
                .andExpect(status().is(expected));
    }
    void correctBirthDate(long registration, String actor, LocalDate date, int expected) throws Exception {
        mvc.perform(patch("/api/registration-requests/" + registration + "/birth-date")
                        .with(user(actor + "@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json")
                        .content("{\"birthDate\":\"" + date + "\",\"reason\":\"Conferido na ficha da escola\"}"))
                .andExpect(status().is(expected));
    }

    @Test void approvingAStudentRequiresTheIdentityCheck() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(payload("200", LocalDate.now().minusYears(19), ""))).andExpect(status().isCreated());
        long registration = requests.findAll().stream().filter(r -> r.getRa().equals("200")).findFirst().orElseThrow().getId();
        for (String body : new String[] {"{\"status\":\"APPROVED\"}", "{\"status\":\"APPROVED\",\"identityChecked\":false}"}) {
            mvc.perform(patch("/api/registration-requests/" + registration)
                            .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(RegistrationStatus.PENDING, requests.findById(registration).orElseThrow().getStatus());
        mvc.perform(patch("/api/registration-requests/" + registration)
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk());
    }

    @Test void adultBecomingMinorOpensTheGuardianFlowAndAsksTheStudentForTheGuardian() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(payload("210", LocalDate.now().minusYears(19), ""))).andExpect(status().isCreated());
        long registration = requests.findAll().stream().filter(r -> r.getRa().equals("210")).findFirst().orElseThrow().getId();
        assertTrue(confirmations.findByRegistrationId(registration).isEmpty());

        correctBirthDate(registration, "outsider", LocalDate.now().minusYears(16), 403);
        correctBirthDate(registration, "coordinator", LocalDate.now().minusYears(14), 400);
        correctBirthDate(registration, "coordinator", LocalDate.now().minusYears(16), 200);

        var saved = requests.findById(registration).orElseThrow();
        assertEquals(LocalDate.now().minusYears(16), saved.getBirthDate());
        assertNotNull(saved.getBirthDateCorrectedAt());
        assertEquals("Conferido na ficha da escola", saved.getBirthDateCorrectionReason());
        var confirmation = confirmations.findByRegistrationId(registration).orElseThrow();
        assertEquals(GuardianStatus.CORRECTION_REQUESTED, confirmation.getStatus());
        assertNull(confirmation.getGuardianEmail());
        var mail = lastMail();
        assertEquals("aluno210@example.com", mail.email());

        review(registration, "coordinator", "APPROVED", 409);
        mvc.perform(post("/api/guardian-corrections/" + token(mail)).contentType("application/json")
                .content("{\"name\":\"Ana Responsavel\",\"email\":\"ana@example.com\"}")).andExpect(status().isOk());
        long id = confirmation.getId();
        assertEquals(GuardianStatus.AWAITING_SCHOOL_CHECK, statusOf(id));
        verify(id, "coordinator", 200);
        decide(token(lastMail()), CONFIRM, 200);
        review(registration, "coordinator", "APPROVED", 200);
    }

    @Test void minorBecomingAdultDropsTheUnverifiedGuardianProcess() throws Exception {
        long registration = register("220");
        long id = confirmationId(registration);
        verify(id, "coordinator", 200);
        var token = token(lastMail());
        review(registration, "coordinator", "APPROVED", 409);

        correctBirthDate(registration, "coordinator", LocalDate.now().minusYears(20), 200);
        assertTrue(confirmations.findByRegistrationId(registration).isEmpty());
        mvc.perform(get("/api/guardian-invitations/" + token)).andExpect(status().isGone());
        review(registration, "coordinator", "APPROVED", 200);
        correctBirthDate(registration, "coordinator", LocalDate.now().minusYears(16), 409);
    }

    @Test void divergenceGoesBackToTheStudentByEmailAndNoInvitationIsSent() throws Exception {
        long registration = register("120");
        long id = confirmationId(registration);
        mvc.perform(post("/api/guardian-confirmations/" + id + "/request-correction")
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"reason\":\"E-mail não consta na ficha da escola\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CORRECTION_REQUESTED"));
        assertEquals(1, events.stream(NotificationMailRequested.class).count());
        var mail = lastMail();
        assertEquals("aluno120@example.com", mail.email(), "the correction link goes to the student");
        assertFalse(mail.text().contains("maria@example.com"), "guardian data is not repeated in the student e-mail");
        assertTrue(mail.text().contains("E-mail não consta na ficha da escola"));
        assertTrue(invitations.findFirstByConfirmationIdOrderByIdDesc(id).isEmpty());
        verify(id, "coordinator", 409);

        var token = token(mail);
        mvc.perform(get("/api/guardian-corrections/" + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Aluno Menor"))
                .andExpect(jsonPath("$.reason").value("E-mail não consta na ficha da escola"))
                .andExpect(jsonPath("$.guardianEmail").doesNotExist());
        mvc.perform(post("/api/guardian-corrections/" + token).contentType("application/json")
                .content("{\"name\":\"Maria\",\"email\":\"aluno120@example.com\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/guardian-corrections/" + token).contentType("application/json")
                .content("{\"name\":\"Maria Correta\",\"email\":\"Maria.Correta@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_SCHOOL_CHECK"));
        var corrected = confirmations.findById(id).orElseThrow();
        assertEquals("maria.correta@example.com", corrected.getGuardianEmail());
        assertEquals(1, corrected.getCorrectionCount());
        mvc.perform(get("/api/guardian-corrections/" + token)).andExpect(status().isGone());
        mvc.perform(post("/api/guardian-corrections/" + token).contentType("application/json")
                .content("{\"name\":\"Outra Pessoa\",\"email\":\"outra@example.com\"}")).andExpect(status().isGone());

        verify(id, "coordinator", 200);
        assertEquals("maria.correta@example.com", lastMail().email());
    }

    @Test void correctionAfterInvitationInvalidatesTheOldLinkAndLoopsAreLimited() throws Exception {
        long id = confirmationId(register("130"));
        verify(id, "coordinator", 200);
        var oldToken = token(lastMail());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/guardian-confirmations/" + id + "/request-correction")
                            .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                            .contentType("application/json").content("{\"reason\":\"Divergente\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CORRECTION_REQUESTED"));
            if (i == 0) mvc.perform(get("/api/guardian-invitations/" + oldToken)).andExpect(status().isGone());
            mvc.perform(post("/api/guardian-corrections/" + token(lastMail())).contentType("application/json")
                    .content("{\"name\":\"Maria " + i + "\",\"email\":\"maria" + i + "@example.com\"}")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/guardian-confirmations/" + id + "/request-correction")
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"reason\":\"Ainda divergente\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test void declineKeepsRegistrationRestrictedAndStopsInvitations() throws Exception {
        long registration = register("140");
        long id = confirmationId(registration);
        verify(id, "coordinator", 200);
        var token = token(lastMail());
        decide(token, "{\"decision\":\"NOT_RECOGNIZED\"}", 200);
        assertEquals(GuardianStatus.DECLINED, statusOf(id));
        mvc.perform(post("/api/guardian-confirmations/" + id + "/resend")
                .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))).andExpect(status().isConflict());
        review(registration, "coordinator", "APPROVED", 409);
        decide(token, CONFIRM, 410);
    }

    @Test void resendReplacesPreviousInvitationAndIsLimited() throws Exception {
        long id = confirmationId(register("150"));
        verify(id, "coordinator", 200);
        var first = token(lastMail());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/guardian-confirmations/" + id + "/resend")
                    .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))).andExpect(status().isOk());
        }
        var latest = token(lastMail());
        assertNotEquals(first, latest);
        mvc.perform(get("/api/guardian-invitations/" + first)).andExpect(status().isGone());
        mvc.perform(get("/api/guardian-invitations/" + latest)).andExpect(status().isOk());
        mvc.perform(post("/api/guardian-confirmations/" + id + "/resend")
                .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))).andExpect(status().isConflict());
    }

    @Test void expiredInvitationIsNeitherConfirmationNorRefusal() throws Exception {
        long registration = register("160");
        long id = confirmationId(registration);
        verify(id, "coordinator", 200);
        var token = token(lastMail());
        var invitation = invitations.findFirstByConfirmationIdOrderByIdDesc(id).orElseThrow();
        invitation.setExpiresAt(Instant.now().minusSeconds(60));
        invitations.saveAndFlush(invitation);
        mvc.perform(get("/api/guardian-invitations/" + token)).andExpect(status().isGone());
        decide(token, CONFIRM, 410);
        mvc.perform(get("/api/guardian-confirmations").param("status", "EXPIRED")
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("EXPIRED"));
        review(registration, "coordinator", "APPROVED", 409);
        mvc.perform(post("/api/guardian-confirmations/" + id + "/resend")
                .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))).andExpect(status().isOk());
        decide(token(lastMail()), CONFIRM, 200);
    }

    @Test void onlyTheOwnSchoolCoordinatorOrAdminWithJustificationMayCheck() throws Exception {
        long id = confirmationId(register("170"));
        verify(id, "outsider", 403);
        mvc.perform(post("/api/guardian-confirmations/" + id + "/verify").with(user("admin@test.com").roles("ADMIN"))
                .contentType("application/json").content("{\"method\":\"Suporte\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/guardian-confirmations/" + id + "/verify").with(user("teacher@test.com").roles("TEACHER"))
                .contentType("application/json").content("{\"method\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/guardian-confirmations").with(user("outsider@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        assertEquals(GuardianStatus.AWAITING_SCHOOL_CHECK, statusOf(id));
        verify(id, "admin", 200);
    }

    @Test void rejectingTheRegistrationInvalidatesPendingLinks() throws Exception {
        long registration = register("180");
        long id = confirmationId(registration);
        verify(id, "coordinator", 200);
        var token = token(lastMail());
        review(registration, "coordinator", "REJECTED", 200);
        mvc.perform(get("/api/guardian-invitations/" + token)).andExpect(status().isGone());
    }
}

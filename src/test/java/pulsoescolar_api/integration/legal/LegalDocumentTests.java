package pulsoescolar_api.integration.legal;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.agreement.SchoolDataAgreementRepository;
import pulsoescolar_api.repository.legal.LegalDocumentRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.*;
import pulsoescolar_api.repository.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class LegalDocumentTests {
    static final String BODY = "{\"title\":\"Titulo\",\"content\":\"Texto do documento\"}";
    static final String BODY_V2 = "{\"title\":\"Titulo\",\"content\":\"Texto revisado\"}";

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired RegistrationRepository requests;
    @Autowired LegalDocumentRepository documents;
    @Autowired SchoolDataAgreementRepository agreements;
    @Autowired TermsRepository terms;
    @Autowired TermsVersionRepository versions;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;
    School school;

    SchoolUser actor(String name, Role role, School school) {
        var u = new SchoolUser(); u.setFullName(name); u.setRa(name); u.setEmail(name + "@test.com");
        u.setPasswordHash("unused"); u.setRole(role); u.setSchool(school); return users.saveAndFlush(u);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        agreements.deleteAll();
        documents.deleteAll();
        school = new School(); school.setNome("Escola"); school.setLogradouro("Rua");
        school.setBairro("Centro"); school.setCidade("Recife"); school = schools.saveAndFlush(school);
        actor("admin", Role.ADMIN, null);
        actor("coordinator", Role.PEDAGOGICAL_COORDINATOR, school);
        var term = terms.lockCurrent(); term.setVersion(1); term.setTitle("Termos"); term.setContent("Conteúdo");
        terms.saveAndFlush(term);
        if (!versions.existsById(1L)) versions.saveAndFlush(new TermsVersion(1L, "Termos", "Conteúdo"));
    }

    String publish(String method, String slug, String body, String who, String role, int expected) throws Exception {
        var request = method.equals("POST") ? post("/api/" + slug) : put("/api/" + slug);
        return mvc.perform(request.with(user(who + "@test.com").roles(role)).contentType("application/json").content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"privacy-policy", "guardian-term", "student-term", "data-agreement"})
    void adminCreatesThenVersionsAndOlderVersionsStayOnRecord(String slug) throws Exception {
        mvc.perform(get("/api/" + slug).with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isNotFound());
        publish("PUT", slug, BODY, "admin", "ADMIN", 404);
        publish("POST", slug, BODY, "admin", "ADMIN", 201);
        publish("POST", slug, BODY, "admin", "ADMIN", 409);
        mvc.perform(put("/api/" + slug).with(user("admin@test.com").roles("ADMIN")).contentType("application/json")
                        .content(BODY_V2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.1"))
                .andExpect(jsonPath("$.content").value("Texto revisado"));
        mvc.perform(get("/api/" + slug + "/history").with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value("1.0")).andExpect(jsonPath("$[1].version").value("1.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"privacy-policy", "guardian-term", "student-term", "data-agreement"})
    void onlyAdminCanPublish(String slug) throws Exception {
        publish("POST", slug, BODY, "coordinator", "PEDAGOGICAL_COORDINATOR", 403);
        mvc.perform(post("/api/" + slug).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        assertEquals(0, documents.count());
    }

    @Test void blankTitleOrContentIsRejected() throws Exception {
        publish("POST", "privacy-policy", "{\"title\":\"\",\"content\":\"x\"}", "admin", "ADMIN", 400);
        publish("POST", "privacy-policy", "{\"title\":\"x\",\"content\":\"\"}", "admin", "ADMIN", 400);
    }

    @Test void privacyPolicyAndGuardianTermArePublicButTheContractIsNot() throws Exception {
        publish("POST", "privacy-policy", BODY, "admin", "ADMIN", 201);
        publish("POST", "guardian-term", BODY, "admin", "ADMIN", 201);
        publish("POST", "student-term", BODY, "admin", "ADMIN", 201);
        publish("POST", "data-agreement", BODY, "admin", "ADMIN", 201);
        mvc.perform(get("/api/student-term")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.0"));
        mvc.perform(get("/api/privacy-policy")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.0"));
        mvc.perform(get("/api/guardian-term")).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Texto do documento"));
        mvc.perform(get("/api/data-agreement")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data-agreement").with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
    }

    @Test void coordinatorAcceptsTheCurrentContractAndMustAcceptAgainAfterANewVersion() throws Exception {
        var coordinator = user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(get("/api/schools/me/data-agreement").with(coordinator)).andExpect(status().isNotFound());
        publish("POST", "data-agreement", BODY, "admin", "ADMIN", 201);

        mvc.perform(get("/api/schools/me/data-agreement").with(coordinator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0")).andExpect(jsonPath("$.accepted").value(false));
        mvc.perform(post("/api/schools/me/data-agreement/accept").with(coordinator).contentType("application/json")
                        .content("{\"version\":\"1.5\",\"accepted\":true}")).andExpect(status().isConflict());
        mvc.perform(post("/api/schools/me/data-agreement/accept").with(coordinator).contentType("application/json")
                        .content("{\"version\":\"1.0\",\"accepted\":false}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/schools/me/data-agreement/accept").with(user("admin@test.com").roles("ADMIN"))
                        .contentType("application/json").content("{\"version\":\"1.0\",\"accepted\":true}"))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) { // accepting twice keeps a single record
            mvc.perform(post("/api/schools/me/data-agreement/accept").with(coordinator).contentType("application/json")
                            .content("{\"version\":\"1.0\",\"accepted\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        }
        assertEquals(1, agreements.count());
        var saved = agreements.findAll().get(0);
        assertEquals(documents.findAll().get(0).getContentHash(), saved.getContentHash());

        publish("PUT", "data-agreement", BODY_V2, "admin", "ADMIN", 200);
        mvc.perform(get("/api/schools/me/data-agreement").with(coordinator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.1")).andExpect(jsonPath("$.accepted").value(false));
    }

    long pendingTeacher() {
        var request = new RegistrationRequest();
        request.setFullName("Professor Teste"); request.setRa("900"); request.setEmail("prof900@example.com");
        request.setPasswordHash(passwords.encode("Chosen-password-123")); request.setRole(Role.TEACHER);
        request.setSchool(school); request.setTermsVersion(1L); request.setStatus(RegistrationStatus.PENDING);
        request.setRequestedAt(java.time.Instant.now());
        return requests.saveAndFlush(request).getId();
    }

    void approve(long id, int expected) throws Exception {
        mvc.perform(patch("/api/registration-requests/" + id)
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"APPROVED\",\"identityChecked\":true}"))
                .andExpect(status().is(expected));
    }

    String teacherPayload(String privacy) {
        return """
                {"name":"Pessoa Teste","birthDate":"1990-01-01","ra":"901","email":"prof901@example.com",
                 "password":"Chosen-password-123","confirmPassword":"Chosen-password-123","schoolId":%d,
                 "termsAccepted":true,"termsVersion":"1.0","role":"STUDENT"%s}
                """.formatted(school.getId(), privacy);
    }

    void register(String privacy, int expected) throws Exception {
        pulsoescolar_api.integration.support.AgreementFixture.publish(documents,
                pulsoescolar_api.entity.legal.LegalDocumentType.STUDENT_TERM, users.findByEmail("admin@test.com").orElseThrow());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(teacherPayload(privacy)))
                .andExpect(status().is(expected));
    }

    void acceptContractAndApprove(long id) throws Exception {
        publish("POST", "data-agreement", BODY, "admin", "ADMIN", 201);
        mvc.perform(post("/api/schools/me/data-agreement/accept")
                .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                .contentType("application/json").content("{\"version\":\"1.0\",\"accepted\":true}"))
                .andExpect(status().isOk());
        approve(id, 200);
    }

    @Test void studentCannotRegisterWithoutAPublishedPrivacyPolicy() throws Exception {
        register("", 404);
        assertEquals(0, requests.count());
    }

    @Test void studentAcceptsOnlyStudentTermAndGeneralTermUpdatesDoNotBlockStudent() throws Exception {
        publish("POST", "student-term", BODY, "admin", "ADMIN", 201);
        publish("POST", "privacy-policy", BODY, "admin", "ADMIN", 201);
        var student = actor("student", Role.STUDENT, school);
        var asStudent = user("student@test.com").roles("STUDENT");
        String consent = "{\"version\":\"1.0\",\"termsAccepted\":true,\"privacyAccepted\":true,\"privacyVersion\":\"1.0\"}";
        mvc.perform(post("/api/terms/accept").with(asStudent).contentType("application/json").content(consent))
                .andExpect(status().isNoContent());
        student = users.findById(student.getId()).orElseThrow();
        assertEquals(java.util.Set.of(1), student.getAcceptedStudentTermVersions());
        assertTrue(student.getAcceptedTermVersions().isEmpty());
        mvc.perform(put("/api/terms").with(user("admin@test.com").roles("ADMIN"))
                .contentType("application/json").content(BODY_V2)).andExpect(status().isOk());
        assertTrue(users.findById(student.getId()).orElseThrow().isTermsAccepted());
        var teacher = actor("teacher", Role.TEACHER, school);
        teacher.setTermsAccepted(true); users.saveAndFlush(teacher);
        publish("PUT", "student-term", BODY_V2, "admin", "ADMIN", 200);
        assertFalse(users.findById(student.getId()).orElseThrow().isTermsAccepted());
        assertTrue(users.findById(teacher.getId()).orElseThrow().isTermsAccepted());
        mvc.perform(post("/api/terms/accept").with(asStudent).contentType("application/json").content(consent))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/terms/accept").with(asStudent).contentType("application/json")
                .content(consent.replace("\"version\":\"1.0\"", "\"version\":\"1.1\""))).andExpect(status().isNoContent());
    }

    @Test void registrationRequiresAcceptingTheCurrentPrivacyPolicyAndApprovalKeepsIt() throws Exception {
        publish("POST", "privacy-policy", BODY, "admin", "ADMIN", 201);
        register("", 400);
        register(",\"privacyAccepted\":false,\"privacyVersion\":\"1.0\"", 400);
        register(",\"privacyAccepted\":true,\"privacyVersion\":\"1.7\"", 409);
        register(",\"privacyAccepted\":true", 409);
        assertEquals(0, requests.count());
        register(",\"privacyAccepted\":true,\"privacyVersion\":\"1.0\"", 201);
        var created = requests.findAll().get(0);
        assertEquals(1, created.getPrivacyVersion());

        acceptContractAndApprove(created.getId());
        var teacher = users.findByEmail("prof901@example.com").orElseThrow();
        assertTrue(teacher.isTermsAccepted());
        assertEquals(java.util.Set.of(1), teacher.getAcceptedPrivacyVersions());
    }

    @Test void aNewPrivacyPolicyVersionMustBeAcceptedAgainTogetherWithTheTerms() throws Exception {
        publish("POST", "privacy-policy", BODY, "admin", "ADMIN", 201);
        register(",\"privacyAccepted\":true,\"privacyVersion\":\"1.0\"", 201);
        acceptContractAndApprove(requests.findAll().get(0).getId());
        assertTrue(users.findByEmail("prof901@example.com").orElseThrow().isTermsAccepted());

        publish("PUT", "privacy-policy", BODY_V2, "admin", "ADMIN", 200);
        var teacher = users.findByEmail("prof901@example.com").orElseThrow();
        assertFalse(teacher.isTermsAccepted());

        var asTeacher = user("prof901@example.com").roles("STUDENT");
        String terms = "\"version\":\"1.0\",\"termsAccepted\":true";
        mvc.perform(post("/api/terms/accept").with(asTeacher).contentType("application/json")
                .content("{" + terms + "}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/terms/accept").with(asTeacher).contentType("application/json")
                .content("{" + terms + ",\"privacyAccepted\":true,\"privacyVersion\":\"1.0\"}")).andExpect(status().isConflict());
        assertFalse(users.findByEmail("prof901@example.com").orElseThrow().isTermsAccepted());
        mvc.perform(post("/api/terms/accept").with(asTeacher).contentType("application/json")
                .content("{" + terms + ",\"privacyAccepted\":true,\"privacyVersion\":\"1.1\"}")).andExpect(status().isNoContent());
        teacher = users.findByEmail("prof901@example.com").orElseThrow();
        assertTrue(teacher.isTermsAccepted());
        assertEquals(java.util.Set.of(1, 2), teacher.getAcceptedPrivacyVersions());
    }

    @Test void registrationsCannotBeApprovedUntilTheSchoolAcceptsTheCurrentContract() throws Exception {
        long id = pendingTeacher();
        approve(id, 409); // no contract published yet
        publish("POST", "data-agreement", BODY, "admin", "ADMIN", 201);
        approve(id, 409); // published, school has not accepted
        var coordinator = user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(post("/api/schools/me/data-agreement/accept").with(coordinator).contentType("application/json")
                .content("{\"version\":\"1.0\",\"accepted\":true}")).andExpect(status().isOk());
        publish("PUT", "data-agreement", BODY_V2, "admin", "ADMIN", 200);
        approve(id, 409); // a new version needs a new acceptance
        mvc.perform(post("/api/schools/me/data-agreement/accept").with(coordinator).contentType("application/json")
                .content("{\"version\":\"1.1\",\"accepted\":true}")).andExpect(status().isOk());
        approve(id, 200);
    }
}

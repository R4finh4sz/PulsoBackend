package pulsoescolar_api;

import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.terms.*;
import pulsoescolar_api.repository.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class SelfRegistrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired RegistrationRepository requests;
    @Autowired TermsRepository terms;
    @Autowired TermsVersionRepository versions;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationManager authentication;
    @Autowired JsonMapper json;
    MockMvc mvc;
    School school, other;

    School school(String cnpj, String cep, String uf) {
        var s = new School(); s.setNome("Escola " + uf); s.setCnpj(cnpj); s.setLogradouro("Rua");
        s.setBairro("Centro"); s.setCidade("Recife"); s.setCep(cep); s.setUf(uf);
        return schools.saveAndFlush(s);
    }
    void actor(String name, Role role, School school) {
        var u = new SchoolUser(); u.setFullName(name); u.setRa(name); u.setEmail(name + "@test.com");
        u.setPasswordHash("unused"); u.setRole(role); u.setSchool(school); users.saveAndFlush(u);
    }
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        school = school("11111111111111", "50000000", "PE");
        other = school("22222222222222", "60000000", "SP");
        actor("admin", Role.ADMIN, null); actor("coordinator", Role.PEDAGOGICAL_COORDINATOR, school);
        actor("outsider", Role.PEDAGOGICAL_COORDINATOR, other); actor("unassigned", Role.PEDAGOGICAL_COORDINATOR, null);
        actor("teacher", Role.TEACHER, school);
        var t = terms.lockCurrent(); t.setVersion(1); t.setTitle("Termos"); t.setContent("Conteúdo"); terms.saveAndFlush(t);
        versions.saveAndFlush(new TermsVersion(1L, "Termos", "Conteúdo"));
    }
    String payload(String ra, Role role, Long schoolId) {
        return """
                {"name":"Pessoa Teste","birthDate":"2000-01-01","ra":"%s",
                 "email":"person%s@example.com","password":"chosen-password-123","schoolId":%d,
                 "termsAccepted":true,"termsVersion":"1.0","role":"%s",
                 "cep":"50000-000","city":"Recife","state":"PE"}
                """.formatted(ra, ra, schoolId, role);
    }
    long submit(String ra, Role role, Long schoolId) throws Exception {
        var r = mvc.perform(post("/api/auth/register").contentType("application/json").content(payload(ra, role, schoolId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }
    void review(long id, String actor, String decision, int expected) throws Exception {
        mvc.perform(patch("/api/registration-requests/" + id)
                        .with(user(actor + "@test.com").roles(actor.equals("admin") ? "ADMIN" : "PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"" + decision + "\"}"))
                .andExpect(status().is(expected));
    }

    long pending(String ra, Role role, School school) {
        var request = new RegistrationRequest();
        request.setFullName("Pessoa Teste");
        request.setRa(ra);
        request.setEmail("person" + ra + "@example.com");
        request.setPasswordHash(passwords.encode("chosen-password-123"));
        request.setRole(role);
        request.setSchool(school);
        request.setTermsVersion(1L);
        request.setStatus(RegistrationStatus.PENDING);
        request.setRequestedAt(java.time.Instant.now());
        return requests.saveAndFlush(request).getId();
    }

    @Test void pendingCannotLoginAndApprovalPreservesPasswordAndTerms() throws Exception {
        long id = submit("100", Role.STUDENT, school.getId());
        assertTrue(users.findByEmail("person100@example.com").isEmpty());
        assertThrows(AuthenticationException.class, () -> authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("person100@example.com", "chosen-password-123")));
        assertTrue(passwords.matches("chosen-password-123", requests.findById(id).orElseThrow().getPasswordHash()));
        review(id, "coordinator", "APPROVED", 200);
        var u = users.findByEmail("person100@example.com").orElseThrow();
        assertEquals(school.getId(), u.getSchool().getId()); assertEquals(LocalDate.of(2000, 1, 1), u.getBirthDate());
        assertNull(u.getClassroom()); assertTrue(u.isTermsAccepted()); assertEquals(java.util.Set.of(1L), u.getAcceptedTermVersions());
        assertNull(requests.findById(id).orElseThrow().getPasswordHash());
        assertTrue(authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(u.getEmail(), "chosen-password-123")).isAuthenticated());
        review(id, "coordinator", "APPROVED", 409);
    }

    @Test void scopesQueuesAndReviewsByRoleAndSchool() throws Exception {
        long student = submit("101", Role.STUDENT, school.getId());
        long teacher = pending("102", Role.TEACHER, school);
        var vacant = school("33333333333333", "70000000", "PE");
        long coord = pending("103", Role.PEDAGOGICAL_COORDINATOR, vacant);
        long outsider = pending("104", Role.TEACHER, other);
        mvc.perform(get("/api/registration-requests").with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/registration-requests").param("role", "TEACHER").param("size", "1")
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(teacher))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/registration-requests").with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(coord));
        review(coord, "coordinator", "APPROVED", 403); review(outsider, "coordinator", "APPROVED", 403);
        review(student, "admin", "APPROVED", 403); review(student, "unassigned", "APPROVED", 403);
        mvc.perform(get("/api/registration-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registration-requests").with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isForbidden());
        review(coord, "admin", "APPROVED", 200); review(teacher, "coordinator", "APPROVED", 200);
    }

    @Test void rejectionIsFinalAndCreatesNoUser() throws Exception {
        long id = submit("105", Role.STUDENT, school.getId());
        review(id, "coordinator", "PENDING", 400); review(id, "coordinator", "REJECTED", 200);
        assertTrue(users.findByEmail("person105@example.com").isEmpty());
        assertNull(requests.findById(id).orElseThrow().getPasswordHash()); review(id, "coordinator", "APPROVED", 409);
    }

    @Test void rejectsDuplicateEmailOrRaAndAdminRole() throws Exception {
        submit("106", Role.STUDENT, school.getId());
        for (String body : new String[]{payload("106", Role.STUDENT, school.getId()),
                payload("107", Role.STUDENT, school.getId()).replace("person107", "PERSON106"),
                payload("107", Role.STUDENT, school.getId()).replace("person107@example.com", "admin@test.com")}) {
            mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isConflict());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json").content(payload("108", Role.ADMIN, school.getId())))
                .andExpect(status().isForbidden());
    }

    @Test void validatesAgePasswordTermsAndSchool() throws Exception {
        String body = payload("109", Role.STUDENT, school.getId());
        for (String invalid : new String[]{body.replace("2000-01-01", LocalDate.now().minusYears(14).toString()),
                body.replace("chosen-password-123", "short"), body.replace("\"termsAccepted\":true", "\"termsAccepted\":false"),
                body.replace("Pessoa Teste", "Pessoa"), body.replace("\"ra\":\"109\"", "\"ra\":\"abc\""),
                body.replace("chosen-password-123", "á".repeat(40)), body.replace("2000-01-01", "2000-02-31")}) {
            mvc.perform(post("/api/auth/register").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body.replace("1.0", "1.99")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(payload("109", Role.STUDENT, 999999L)))
                .andExpect(status().isNotFound());
    }

    @Test void frontendMultipartAndPhotoWorkWithoutRoleOrPersistedAddress() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var photo = new MockMultipartFile("photo", "avatar.png", "image/png", bytes.toByteArray());
        String body = payload("110", Role.STUDENT, school.getId()).replace(",\"role\":\"STUDENT\"", "");
        var result = mvc.perform(multipart("/api/auth/register").file(photo).param("data", body)).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(Role.STUDENT, requests.findById(id).orElseThrow().getRole());
        mvc.perform(get("/api/registration-requests/" + id + "/photo").with(user("outsider@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/registration-requests/" + id + "/photo").with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        review(id, "coordinator", "APPROVED", 200);
        mvc.perform(get("/api/me/photo").with(user("person110@example.com").roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/auth/register").param("data", payload("111", Role.STUDENT, school.getId())))
                .andExpect(status().isCreated());
        for (String invalid : new String[]{"null", "{"}) {
            mvc.perform(multipart("/api/auth/register").param("data", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/auth/register").file(new MockMultipartFile("photo", "fake.png", "image/png", "invalid".getBytes()))
                        .param("data", payload("112", Role.STUDENT, school.getId()))).andExpect(status().isBadRequest());
        for (Class<?> type : new Class<?>[]{SchoolUser.class, RegistrationRequest.class}) {
            assertTrue(java.util.Arrays.stream(type.getDeclaredFields()).noneMatch(f ->
                    java.util.Set.of("cep", "city", "state", "address").contains(f.getName())));
        }
    }

    @Test void onlyStudentsCanRegisterDirectly() throws Exception {
        mvc.perform(post("/api/students").contentType("application/json")
                        .content(payload("120", Role.STUDENT, school.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/students").contentType("application/json")
                        .content(payload("130", Role.ADMIN, school.getId())))
                .andExpect(status().isBadRequest());
        String[] routes = {"teachers", "coordinators"};
        Role[] roles = {Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR};
        for (int i = 0; i < routes.length; i++) {
            mvc.perform(post("/api/" + routes[i]).contentType("application/json").content(payload("12" + i, roles[i], school.getId())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/" + routes[i]).with(user("admin@test.com").roles("ADMIN"))
                            .contentType("application/json").content(payload("13" + i, roles[i], school.getId())))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/register").contentType("application/json")
                            .content(payload("14" + i, roles[i], school.getId())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void publicSchoolSearchSupportsCepAndCityStateAndOnlyAdminUpdatesLocation() throws Exception {
        mvc.perform(get("/api/terms")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("1.0"));
        mvc.perform(get("/api/schools/search").param("cep", "50000-000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(school.getId().toString()))
                .andExpect(jsonPath("$.content[0].cnpj").doesNotExist());
        mvc.perform(get("/api/schools/search").param("city", "recife").param("state", "pe"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/schools/search").param("cep", "123")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/schools/search").param("size", "101")).andExpect(status().isBadRequest());
        for (String actor : new String[]{"coordinator", "admin"}) {
            mvc.perform(patch("/api/schools/" + school.getId() + "/location")
                            .with(user(actor + "@test.com").roles(actor.equals("admin") ? "ADMIN" : "PEDAGOGICAL_COORDINATOR"))
                            .contentType("application/json").content("{\"cep\":\"50000-001\",\"uf\":\"pe\"}"))
                    .andExpect(status().is(actor.equals("admin") ? 200 : 403));
        }
        assertEquals("50000001", schools.findById(school.getId()).orElseThrow().getCep());
    }

    @Test void termsUpdatedWhilePendingRequireNewAcceptance() throws Exception {
        long id = submit("140", Role.STUDENT, school.getId());
        var term = terms.lockCurrent(); term.setVersion(2); terms.saveAndFlush(term);
        versions.saveAndFlush(new TermsVersion(2L, "Novos termos", "Novo conteúdo"));
        review(id, "coordinator", "APPROVED", 200);
        var u = users.findByEmail("person140@example.com").orElseThrow();
        assertFalse(u.isTermsAccepted()); assertEquals(java.util.Set.of(1L), u.getAcceptedTermVersions());
    }

    @Test void accountDeletionAlsoRemovesNewPersonalDataAndRegistrationCopy() throws Exception {
        long id = submit("150", Role.STUDENT, school.getId());
        requests.findById(id).orElseThrow().setProfilePhoto(new byte[]{1, 2, 3});
        review(id, "coordinator", "APPROVED", 200);
        var created = users.findByEmail("person150@example.com").orElseThrow();
        var result = mvc.perform(post("/api/me/deletion-requests").with(user(created.getEmail()).roles("STUDENT"))
                        .contentType("application/json").content("{\"reason\":\"Quero excluir minha conta\"}"))
                .andExpect(status().isCreated()).andReturn();
        long deletionId = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/account-deletion-requests/" + deletionId)
                        .with(user("coordinator@test.com").roles("PEDAGOGICAL_COORDINATOR"))
                        .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"Confirmado\"}"))
                .andExpect(status().isOk());
        var registration = requests.findById(id).orElseThrow();
        assertNull(created.getBirthDate()); assertNull(created.getProfilePhoto());
        assertNull(registration.getBirthDate()); assertNull(registration.getProfilePhoto());
        assertTrue(registration.getEmail().endsWith("@deleted.invalid"));
    }
}

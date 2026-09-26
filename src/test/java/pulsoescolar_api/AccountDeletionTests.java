package pulsoescolar_api;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import pulsoescolar_api.config.JwtConfig;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.auth.AuthSession;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AccountDeletionTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired AccountDeletionRepository requests;
    @Autowired AuthSessionRepository sessions;
    @Autowired JwtEncoder encoder;
    @Autowired ClassroomRepository classrooms;
    @Autowired pulsoescolar_api.repository.subject.SubjectRepository subjects;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    SchoolUser student;

    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
            user(String email) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email)
                .roles(users.findByEmail(email).orElseThrow().getRole().name());
    }

    School school(String cnpj) {
        var school = new School();
        school.setNome(cnpj); school.setCnpj(cnpj); school.setLogradouro("Rua");
        school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    SchoolUser create(String name, Role role, School school) {
        var user = new SchoolUser();
        user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("old-hash"); user.setRole(role); user.setSchool(school);
        return users.saveAndFlush(user);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var school = school("11111111111111");
        student = create("student", Role.STUDENT, school);
        create("coordinator", Role.PEDAGOGICAL_COORDINATOR, school);
        create("outsider", Role.PEDAGOGICAL_COORDINATOR, school("22222222222222"));
        create("unassigned", Role.PEDAGOGICAL_COORDINATOR, null);
        create("teacher", Role.TEACHER, school);
        create("admin", Role.ADMIN, null);
    }

    Long submit() throws Exception {
        mvc.perform(post("/api/me/deletion-requests").with(user("student@example.com"))
                .contentType("application/json").content("{\"reason\":\"Não utilizo mais a conta\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        return requests.findByRequesterIdOrderByRequestedAtDesc(student.getId()).stream()
                .filter(r -> r.getStatus() == DeletionStatus.PENDING).findFirst().orElseThrow().getId();
    }

    @Test void approvalAnonymizesPreservesIdentityAndRevokesAccess() throws Exception {
        var classroom = new Classroom();
        classroom.setName("Turma"); classroom.setIdentifier("A"); classroom.setSchool(student.getSchool());
        classrooms.saveAndFlush(classroom);
        student.setClassroom(classroom);
        users.saveAndFlush(student);
        var session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUser(student);
        session.setCreatedAt(Instant.now()); session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setTwoFactorVerified(true);
        sessions.saveAndFlush(session);
        var claims = JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(student.getId().toString()).id(session.getId().toString())
                .issuedAt(session.getCreatedAt()).expiresAt(session.getExpiresAt()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        Long schoolId = student.getSchool().getId();
        Long id = submit();
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"Solicitação atendida\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.fullName").value("DeletedUser_" + student.getId()));
        var deleted = users.findById(student.getId()).orElseThrow();
        assertEquals("DeletedUser_" + student.getId(), deleted.getRa());
        assertEquals("DeletedUser_" + student.getId() + "@deleted.invalid", deleted.getEmail());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(schoolId, deleted.getSchool().getId());
        assertEquals(classroom.getId(), deleted.getClassroom().getId());
        assertNotEquals("old-hash", deleted.getPasswordHash());
        assertFalse(sessions.existsById(session.getId()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"student@example.com\",\"password\":\"old-password\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(deleted.getRa(), requests.findById(id).orElseThrow().getReason());
        mvc.perform(get("/api/students/" + student.getId()).with(user("coordinator@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/students/" + student.getId()).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"fullName\":\"Restaurar\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/students").with(user("coordinator@example.com")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"reason\":\"Repetido\"}"))
                .andExpect(status().isConflict());
    }

    @Test void rejectionAllowsNewRequestAndApprovalScrubsPreviousReasons() throws Exception {
        Long id = submit();
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"reason\":\"Verificar com aluno\"}"))
                .andExpect(status().isOk());
        assertNull(student.getDeletedAt());
        assertEquals("student@example.com", student.getEmail());
        Long next = submit();
        mvc.perform(get("/api/me/deletion-requests").with(user("student@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch("/api/account-deletion-requests/" + next).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                .andExpect(status().isOk());
        assertEquals("DeletedUser_" + student.getId(), requests.findById(id).orElseThrow().getReviewReason());
    }

    @Test void teacherUsesSameFlowAndPreservesClassesAndSubjects() throws Exception {
        var teacher = users.findByEmail("teacher@example.com").orElseThrow();
        var classroom = new Classroom();
        classroom.setName("Turma"); classroom.setIdentifier("A"); classroom.setSchool(teacher.getSchool());
        classroom.getTeachers().add(teacher);
        classrooms.saveAndFlush(classroom);
        var subject = new pulsoescolar_api.entity.subject.Subject();
        subject.setName("Matemática"); subject.setClassroom(classroom); subject.setTeacher(teacher);
        subjects.saveAndFlush(subject);
        var session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUser(teacher);
        session.setCreatedAt(Instant.now()); session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setTwoFactorVerified(true);
        sessions.saveAndFlush(session);

        mvc.perform(post("/api/me/deletion-requests").with(user(teacher.getEmail()))
                .contentType("application/json").content("{\"reason\":\"Não leciono mais na escola\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(teacher.getId()))
                .andExpect(jsonPath("$.role").value("TEACHER"));
        Long firstId = requests.findByRequesterIdOrderByRequestedAtDesc(teacher.getId()).getFirst().getId();
        mvc.perform(post("/api/me/deletion-requests").with(user(teacher.getEmail()))
                .contentType("application/json").content("{\"reason\":\"Duplicado\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/me/deletion-requests").with(user("student@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/account-deletion-requests").with(user("coordinator@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].role").value("TEACHER"));
        mvc.perform(get("/api/account-deletion-requests").with(user("outsider@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (String actor : new String[]{"teacher", "outsider"}) {
            mvc.perform(patch("/api/account-deletion-requests/" + firstId).with(user(actor + "@example.com"))
                    .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/account-deletion-requests/" + firstId).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"reason\":\"Esclarecer pedido\"}"))
                .andExpect(status().isOk());
        assertNull(teacher.getDeletedAt());
        assertEquals("teacher@example.com", teacher.getEmail());
        assertTrue(sessions.existsById(session.getId()));
        mvc.perform(post("/api/me/deletion-requests").with(user(teacher.getEmail()))
                .contentType("application/json").content("{\"reason\":\"Confirmo a exclusão\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/me/deletion-requests").with(user(teacher.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        Long nextId = requests.findByRequesterIdOrderByRequestedAtDesc(teacher.getId()).stream()
                .filter(r -> r.getStatus() == DeletionStatus.PENDING).findFirst().orElseThrow().getId();
        mvc.perform(patch("/api/account-deletion-requests/" + nextId).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("DeletedUser_" + teacher.getId()));
        entityManager.flush();
        entityManager.clear();
        var deleted = users.findById(teacher.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
        assertEquals("DeletedUser_" + teacher.getId(), deleted.getRa());
        assertEquals("DeletedUser_" + teacher.getId() + "@deleted.invalid", deleted.getEmail());
        assertFalse(sessions.existsById(session.getId()));
        assertEquals(teacher.getId(), subjects.findById(subject.getId()).orElseThrow().getTeacher().getId());
        assertTrue(classrooms.findById(classroom.getId()).orElseThrow().getTeachers().stream()
                .anyMatch(t -> t.getId().equals(teacher.getId())));
        assertEquals(deleted.getRa(), requests.findById(firstId).orElseThrow().getReason());
        mvc.perform(get("/api/teachers").with(user("coordinator@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(patch("/api/teachers/" + teacher.getId()).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"fullName\":\"Restaurar\"}"))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"coordinator", "unassigned"})
    void coordinatorRequestsAreReviewedOnlyByAdmin(String name) throws Exception {
        var coordinator = users.findByEmail(name + "@example.com").orElseThrow();
        create("peer", Role.PEDAGOGICAL_COORDINATOR, student.getSchool());
        var session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUser(coordinator);
        session.setCreatedAt(Instant.now()); session.setExpiresAt(Instant.now().plusSeconds(600));
        sessions.saveAndFlush(session);
        String body = "{\"reason\":\"Solicito exclusão da conta\"}";
        mvc.perform(post("/api/me/deletion-requests").with(user(coordinator.getEmail()))
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("PEDAGOGICAL_COORDINATOR"));
        Long id = requests.findByRequesterIdOrderByRequestedAtDesc(coordinator.getId()).getFirst().getId();
        mvc.perform(post("/api/me/deletion-requests").with(user(coordinator.getEmail()))
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/me/deletion-requests").with(user(coordinator.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/account-deletion-requests").with(user("admin@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(coordinator.getId()));
        for (String actor : new String[]{"coordinator", "peer", "outsider"}) {
            mvc.perform(get("/api/account-deletion-requests").with(user(actor + "@example.com")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        for (String actor : new String[]{name, "peer", "outsider", "teacher", "student"}) {
            for (String decision : new String[]{"APPROVED", "REJECTED"}) {
                mvc.perform(patch("/api/account-deletion-requests/" + id).with(user(actor + "@example.com"))
                        .contentType("application/json").content("{\"status\":\"" + decision + "\",\"reason\":\"OK\"}"))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("admin@example.com"))
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"reason\":\"Esclarecer pedido\"}"))
                .andExpect(status().isOk());
        assertNull(coordinator.getDeletedAt());
        assertTrue(sessions.existsById(session.getId()));
        mvc.perform(post("/api/me/deletion-requests").with(user(coordinator.getEmail()))
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        Long nextId = requests.findByRequesterIdOrderByRequestedAtDesc(coordinator.getId()).stream()
                .filter(r -> r.getStatus() == DeletionStatus.PENDING).findFirst().orElseThrow().getId();
        mvc.perform(patch("/api/account-deletion-requests/" + nextId).with(user("admin@example.com"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("DeletedUser_" + coordinator.getId()));
        assertNotNull(coordinator.getDeletedAt());
        assertEquals("DeletedUser_" + coordinator.getId(), coordinator.getRa());
        assertEquals("DeletedUser_" + coordinator.getId() + "@deleted.invalid", coordinator.getEmail());
        assertFalse(sessions.existsById(session.getId()));
        assertEquals(users.findByEmail("admin@example.com").orElseThrow().getId(),
                requests.findById(nextId).orElseThrow().getReviewedBy().getId());
    }

    @Test void staleUpdateCannotRestorePersonalDataAfterApproval() throws Exception {
        entityManager.detach(student);
        Long id = submit();
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                .andExpect(status().isOk());
        student.setFullName("Atualização concorrente");
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> users.saveAndFlush(student));
    }

    @Test void enforcesSchoolScopeRolesValidationAndPendingUniqueness() throws Exception {
        Long id = submit();
        mvc.perform(post("/api/me/deletion-requests").with(user("student@example.com"))
                .contentType("application/json").content("{\"reason\":\"Outro pedido\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/me/deletion-requests").with(user("student@example.com"))
                .contentType("application/json").content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/deletion-requests").with(user("admin@example.com"))
                .contentType("application/json").content("{\"reason\":\"Excluir\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/account-deletion-requests").with(user("coordinator@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/account-deletion-requests").with(user("outsider@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (String actor : new String[]{"outsider", "unassigned", "teacher", "student"}) {
            mvc.perform(patch("/api/account-deletion-requests/" + id).with(user(actor + "@example.com"))
                    .contentType("application/json").content("{\"status\":\"APPROVED\",\"reason\":\"OK\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/account-deletion-requests").param("size", "101").with(user("coordinator@example.com")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/account-deletion-requests/" + id).with(user("coordinator@example.com"))
                .contentType("application/json").content("{\"status\":\"PENDING\",\"reason\":\"OK\"}"))
                .andExpect(status().isBadRequest());
    }
}

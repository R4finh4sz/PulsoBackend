package pulsoescolar_api.integration.classroom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.classroom.ClassroomDeliberationRepository;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;

@SpringBootTest
@Transactional
class ClassroomDeliberationTests {
    private static final String URL = "/api/classroom-deliberations";
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ClassroomRepository classrooms;
    @Autowired SchoolRepository schools;
    @Autowired ClassroomDeliberationRepository deliberations;
    @Autowired EntityManager entityManager;
    MockMvc mvc;
    School school;
    Classroom origin, next, repeat, foreign;
    SchoolUser approved, failed;
    RequestPostProcessor coordinator = user("coord@example.com").roles("PEDAGOGICAL_COORDINATOR");

    School school(String name) {
        var created = new School();
        created.setNome(name); created.setLogradouro("Rua A"); created.setBairro("Centro"); created.setCidade("Recife");
        return schools.saveAndFlush(created);
    }

    SchoolUser create(String name, Role role, School owner) {
        var created = new SchoolUser();
        created.setFullName(name); created.setRa(name); created.setEmail(name + "@example.com");
        created.setPasswordHash("unused"); created.setRole(role); created.setSchool(owner);
        return users.saveAndFlush(created);
    }

    Classroom room(String name, School owner) {
        var created = new Classroom();
        created.setName(name); created.setIdentifier("A"); created.setSchool(owner);
        return classrooms.saveAndFlush(created);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        school = school("Escola");
        create("coord", Role.PEDAGOGICAL_COORDINATOR, school);
        create("teacher", Role.TEACHER, school);
        origin = room("2º ano · Manhã", school);
        next = room("3º ano · Manhã", school);
        repeat = room("2º ano · Tarde", school);
        foreign = room("3º ano · Noite", school("Outra escola"));
        approved = create("approved", Role.STUDENT, school);
        failed = create("failed", Role.STUDENT, school);
        approved.setClassroom(origin);
        failed.setClassroom(origin);
        users.flush();
    }

    String body(String action, String first, String second) {
        return """
                {"classroomId":%d,"action":"%s","students":[%s,%s]}
                """.formatted(origin.getId(), action, first, second);
    }

    String student(SchoolUser student, String result, Object destination) {
        return """
                {"studentId":%d,"result":"%s","destinationClassroomId":%s}
                """.formatted(student.getId(), result, destination);
    }

    void assertUnchanged() {
        entityManager.flush(); entityManager.clear();
        assertEquals(origin.getId(), users.findById(approved.getId()).orElseThrow().getClassroom().getId());
        assertEquals(origin.getId(), users.findById(failed.getId()).orElseThrow().getClassroom().getId());
        assertEquals(0, deliberations.count());
    }

    @Test void transfersStudentsToDifferentClassroomsAndRecordsTheDeliberation() throws Exception {
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("TRANSFER", student(approved, "APPROVED", next.getId()), student(failed, "FAILED", repeat.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.action").value("TRANSFER"))
                .andExpect(jsonPath("$.students.length()").value(2));
        entityManager.flush(); entityManager.clear();
        assertEquals(next.getId(), users.findById(approved.getId()).orElseThrow().getClassroom().getId());
        assertEquals(repeat.getId(), users.findById(failed.getId()).orElseThrow().getClassroom().getId());
        assertNull(classrooms.findById(origin.getId()).orElseThrow().getDeletedAt());
        var saved = deliberations.findAll().getFirst();
        assertEquals(origin.getId(), saved.getClassroom().getId());
        assertEquals("coord@example.com", saved.getPerformedBy().getEmail());
        assertNotNull(saved.getPerformedAt());
        assertEquals(2, saved.getEntries().size());
    }

    @Test void resubmittingTheSameConfirmationDoesNotDuplicateAnything() throws Exception {
        String payload = body("TRANSFER", student(approved, "APPROVED", next.getId()), student(failed, "FAILED", repeat.getId()));
        mvc.perform(post(URL).with(coordinator).contentType("application/json").content(payload)).andExpect(status().isCreated());
        mvc.perform(post(URL).with(coordinator).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        assertEquals(1, deliberations.count());
    }

    @Test void rejectsIncompleteDuplicateAndForeignStudents() throws Exception {
        var outsider = create("outsider", Role.STUDENT, school);
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content("{\"classroomId\":%d,\"action\":\"TRANSFER\",\"students\":[%s]}".formatted(
                                origin.getId(), student(approved, "APPROVED", next.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("TRANSFER", student(approved, "APPROVED", next.getId()), student(approved, "FAILED", repeat.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("TRANSFER", student(approved, "APPROVED", next.getId()), student(outsider, "FAILED", repeat.getId()))))
                .andExpect(status().isBadRequest());
        assertUnchanged();
    }

    @Test void rejectsInvalidDestinationsWithoutApplyingAnyChange() throws Exception {
        for (Object destination : new Object[] {"null", origin.getId(), foreign.getId(), 999999L}) {
            int expected = destination.equals(999999L) ? 404 : 400;
            mvc.perform(post(URL).with(coordinator).contentType("application/json")
                            .content(body("TRANSFER", student(approved, "APPROVED", next.getId()), student(failed, "FAILED", destination))))
                    .andExpect(status().is(expected));
        }
        assertUnchanged();
    }

    @Test void rejectsEmptyClassroom() throws Exception {
        var empty = room("1º ano · Manhã", school);
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content("{\"classroomId\":%d,\"action\":\"TRANSFER\",\"students\":[%s]}".formatted(
                                empty.getId(), student(approved, "APPROVED", next.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test void graduatesClassroomKeepingStudentRecords() throws Exception {
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("GRADUATE", student(approved, "APPROVED", null), student(failed, "APPROVED", null))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.action").value("GRADUATE"));
        entityManager.flush(); entityManager.clear();
        var closed = classrooms.findById(origin.getId()).orElseThrow();
        assertNotNull(closed.getGraduatedAt());
        assertNotNull(closed.getDeletedAt());
        var student = users.findById(approved.getId()).orElseThrow();
        assertNull(student.getDeletedAt());
        assertNull(student.getClassroom());
        assertEquals(1, deliberations.count());
    }

    @Test void graduationRequiresEveryoneApprovedWithoutDestination() throws Exception {
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("GRADUATE", student(approved, "APPROVED", null), student(failed, "FAILED", null))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(coordinator).contentType("application/json")
                        .content(body("GRADUATE", student(approved, "APPROVED", null), student(failed, "APPROVED", next.getId()))))
                .andExpect(status().isBadRequest());
        assertUnchanged();
        assertNull(classrooms.findById(origin.getId()).orElseThrow().getGraduatedAt());
    }

    @Test void validatesPayloadAndPermissions() throws Exception {
        mvc.perform(post(URL).with(coordinator).contentType("application/json").content("{\"students\":[]}"))
                .andExpect(status().isBadRequest());
        String payload = body("TRANSFER", student(approved, "APPROVED", next.getId()), student(failed, "FAILED", repeat.getId()));
        mvc.perform(post(URL).with(user("teacher@example.com").roles("TEACHER")).contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        assertUnchanged();
    }
}

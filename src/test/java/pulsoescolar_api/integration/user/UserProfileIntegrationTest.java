package pulsoescolar_api.integration.user;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class UserProfileIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired ClassroomRepository classrooms;
    @Autowired AccountDeletionRepository requests;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    SchoolUser create(Role role) {
        var account = new SchoolUser();
        var id = UUID.randomUUID().toString();
        account.setFullName("Perfil");
        account.setRa(id);
        account.setEmail(id + "@example.com");
        account.setPasswordHash("private-hash");
        account.setRole(role);
        return users.saveAndFlush(account);
    }

    School school() {
        var school = new School();
        school.setNome("Escola do perfil");
        school.setCnpj("12345678901234");
        school.setLogradouro("Rua");
        school.setBairro("Centro");
        school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    Classroom room(School school) {
        var room = new Classroom();
        room.setName(UUID.randomUUID().toString());
        room.setIdentifier("A");
        room.setSchool(school);
        return classrooms.saveAndFlush(room);
    }

    void deletion(SchoolUser account, DeletionStatus status, Instant time) {
        var request = new AccountDeletionRequest();
        request.setRequester(account);
        request.setReason("Pedido");
        request.setStatus(status);
        request.setRequestedAt(time);
        requests.saveAndFlush(request);
    }

    @Test
    void studentProfileContainsOwnLinksAndLatestDeletionWithoutCredentials() throws Exception {
        var student = create(Role.STUDENT);
        var school = school();
        var room = room(school);
        student.setSchool(school);
        student.setClassroom(room);
        deletion(student, DeletionStatus.REJECTED, Instant.parse("2026-01-01T00:00:00Z"));
        deletion(student, DeletionStatus.PENDING, Instant.parse("2026-02-01T00:00:00Z"));
        deletion(create(Role.STUDENT), DeletionStatus.REJECTED, Instant.parse("2026-03-01T00:00:00Z"));

        mvc.perform(get("/api/me/profile").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(student.getId()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.schools.length()").value(1))
                .andExpect(jsonPath("$.schools[0].id").value(school.getId().toString()))
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andExpect(jsonPath("$.classrooms[0].id").value(room.getId()))
                .andExpect(jsonPath("$.deletionRequest.userId").value(student.getId()))
                .andExpect(jsonPath("$.deletionRequest.status").value("PENDING"));
    }

    @Test
    void teacherProfileOnlyContainsAssignedClassrooms() throws Exception {
        var teacher = create(Role.TEACHER);
        var room = room(null);
        room.getTeachers().add(teacher);
        room(null);
        mvc.perform(get("/api/me/profile").with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andExpect(jsonPath("$.classrooms[0].id").value(room.getId()));
    }

    @Test
    void coordinatorProfileOnlyContainsSchoolClassrooms() throws Exception {
        var coordinator = create(Role.PEDAGOGICAL_COORDINATOR);
        var school = school();
        coordinator.setSchool(school);
        var room = room(school);
        room(null);
        mvc.perform(get("/api/me/profile").with(user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andExpect(jsonPath("$.classrooms[0].id").value(room.getId()));
    }

    @Test
    void profilesWithoutLinksReturnEmptyListsAndNoDeletion() throws Exception {
        room(null);
        for (var role : Role.values()) {
            var account = create(role);
            mvc.perform(get("/api/me/profile").with(user(account.getEmail()).roles(role.name())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.schools").isEmpty())
                    .andExpect(jsonPath("$.classrooms").isEmpty())
                    .andExpect(jsonPath("$.deletionRequest").doesNotExist());
        }
    }

    @Test
    void anonymousProfileIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
    }
}

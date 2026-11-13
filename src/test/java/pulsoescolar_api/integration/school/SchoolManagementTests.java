package pulsoescolar_api.integration.school;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class SchoolManagementTests {
    @Autowired WebApplicationContext context;
    @Autowired SchoolRepository schools;
    @Autowired UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired pulsoescolar_api.repository.classroom.ClassroomRepository classrooms;
    @Autowired pulsoescolar_api.repository.user.StudentTransferRepository transfers;
    MockMvc mvc;
    School school;
    SchoolUser admin;
    SchoolUser coordinator;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        school = createSchool("Escola A");
        admin = createUser("admin", Role.ADMIN);
        coordinator = createUser("coordinator", Role.PEDAGOGICAL_COORDINATOR);
    }

    @Test void deactivatesIdempotentlyAndRemovesDeleteRoute() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch("/api/schools/{id}/deactivate", school.getId())
                    .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNoContent());
        }
        assertFalse(schools.findById(school.getId()).orElseThrow().isActive());
        mvc.perform(delete("/api/schools/{id}", school.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/schools/{id}/deactivate", Long.MAX_VALUE)
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void deactivationPreservesLinksHidesSchoolAndSignalsUser() throws Exception {
        coordinator.setSchool(school);
        coordinator.setPasswordHash(passwords.encode("Password123"));
        users.flush();
        mvc.perform(patch("/api/schools/{id}/deactivate", school.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isNoContent());
        assertEquals(school.getId(), coordinator.getEnrollmentSchool().getId());
        assertNull(coordinator.getSchool());
        assertFalse(coordinator.belongsToSchool(school.getId()));
        mvc.perform(get("/api/schools").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/schools/search"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/me").with(user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolUnavailable").value(true))
                .andExpect(jsonPath("$.schoolId").isEmpty());
        mvc.perform(get("/api/classrooms").with(user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + coordinator.getEmail() + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.schoolUnavailable").value(true))
                .andExpect(jsonPath("$.user.schoolId").isEmpty());
    }

    @Test void replacesCoordinatorAndRemovesPreviousAccess() throws Exception {
        var previous = createUser("previous", Role.PEDAGOGICAL_COORDINATOR);
        previous.setSchool(school);
        users.flush();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch("/api/schools/{id}/coordinator", school.getId())
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .contentType("application/json").content("{\"coordinatorId\":" + coordinator.getId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coordinator.id").value(coordinator.getId()));
        }
        assertNull(previous.getEnrollmentSchool());
        assertNull(previous.getSchool());
        assertFalse(previous.belongsToSchool(school.getId()));
        assertTrue(coordinator.belongsToSchool(school.getId()));
        mvc.perform(get("/api/schools").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].coordinator.id").value(coordinator.getId()));
    }

    @Test void validatesCoordinatorAndSchool() throws Exception {
        for (String body : new String[]{"{}", "{\"coordinatorId\":0}",
                "{\"coordinatorId\":" + admin.getId() + "}"}) {
            mvc.perform(patch("/api/schools/{id}/coordinator", school.getId())
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        coordinator.setDeletedAt(java.time.Instant.now());
        users.flush();
        updateCoordinator(school.getId(), coordinator.getId(), 400);
        coordinator.setDeletedAt(null);
        coordinator.setSchool(createSchool("Escola B"));
        users.flush();
        updateCoordinator(school.getId(), coordinator.getId(), 409);
        updateCoordinator(school.getId(), Long.MAX_VALUE, 404);
        updateCoordinator(Long.MAX_VALUE, coordinator.getId(), 404);
    }

    @Test void onlyAdminCanUseNewRoutes() throws Exception {
        for (Role role : new Role[]{Role.PEDAGOGICAL_COORDINATOR, Role.TEACHER, Role.STUDENT}) {
            mvc.perform(patch("/api/schools/{id}/deactivate", school.getId())
                    .with(user("actor").roles(role.name()))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), coordinator.getId())
                    .with(user("actor").roles(role.name()))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/schools/{id}/coordinator", school.getId())
                    .with(user("actor").roles(role.name())).contentType("application/json")
                    .content("{\"coordinatorId\":" + coordinator.getId() + "}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/schools/{id}/deactivate", school.getId())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), coordinator.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/schools/{id}/coordinator", school.getId())
                .contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
    }

    @Test void reassignsAllProfilesAfterSchoolDeactivation() throws Exception {
        coordinator.setSchool(school);
        var student = createUser("student", Role.STUDENT);
        student.setSchool(school);
        var room = new pulsoescolar_api.entity.classroom.Classroom();
        room.setName("1 Matutino"); room.setIdentifier("B"); room.setSchool(school);
        classrooms.saveAndFlush(room);
        student.setClassroom(room);
        var teacher = createUser("teacher", Role.TEACHER);
        teacher.setSchool(school);
        users.flush();
        var destination = createSchool("Escola B");
        var pending = new StudentTransfer();
        pending.setStudent(student); pending.setOrigin(school); pending.setDestination(destination);
        pending.setRequestedBy(coordinator); pending.setRequestedAt(java.time.Instant.now());
        pending.setStatus(RegistrationStatus.PENDING);
        transfers.saveAndFlush(pending);
        assertTrue(transfers.existsByStudentIdAndStatus(student.getId(), RegistrationStatus.PENDING));
        school.setActive(false);
        for (var account : java.util.List.of(student, teacher)) {
            mvc.perform(patch("/api/schools/{id}/users/{userId}", destination.getId(), account.getId())
                    .with(user(admin.getEmail()).roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.schoolId").value(destination.getId()))
                    .andExpect(jsonPath("$.schoolUnavailable").value(false));
            assertTrue(account.belongsToSchool(destination.getId()));
            assertFalse(account.belongsToSchool(school.getId()));
        }
        updateCoordinator(destination.getId(), coordinator.getId(), 200);
        assertTrue(coordinator.belongsToSchool(destination.getId()));
        assertFalse(coordinator.isSchoolUnavailable());
        assertNull(student.getClassroom());
        assertTrue(classrooms.existsById(room.getId()));
        assertFalse(transfers.existsByStudentIdAndStatus(student.getId(), RegistrationStatus.PENDING));
        assertTrue(transfers.existsById(pending.getId()));
    }

    @Test void teacherCanSwitchToAnotherActiveSchoolButCannotAccessInactiveClassrooms() throws Exception {
        var teacher = createUser("teacher", Role.TEACHER);
        teacher.setSchool(school);
        var room = new pulsoescolar_api.entity.classroom.Classroom();
        room.setName("1 Matutino"); room.setIdentifier("B"); room.setSchool(school);
        room.getTeachers().add(teacher);
        classrooms.saveAndFlush(room);
        var other = createSchool("Escola B");
        teacher.getSchools().add(other);
        school.setActive(false);
        users.flush();
        var actor = user(teacher.getEmail()).roles("TEACHER");
        mvc.perform(get("/api/me/profile").with(actor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.schools.length()").value(1))
                .andExpect(jsonPath("$.schools[0].id").value(other.getId()));
        mvc.perform(get("/api/classrooms/{id}", room.getId()).with(actor)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + school.getId() + "}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + other.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolUnavailable").value(false));
    }

    @Test void rejectsInactiveDestinationsAndInvalidReassignments() throws Exception {
        var student = createUser("student", Role.STUDENT);
        student.setSchool(createSchool("Escola B"));
        users.flush();
        mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), student.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), admin.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isBadRequest());
        student.setDeletedAt(java.time.Instant.now());
        users.flush();
        mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), student.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isBadRequest());
        school.setActive(false);
        users.flush();
        updateCoordinator(school.getId(), coordinator.getId(), 409);
        mvc.perform(patch("/api/schools/{id}/users/{userId}", school.getId(), student.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isConflict());
    }

    private void updateCoordinator(Long schoolId, Long coordinatorId, int expectedStatus) throws Exception {
        mvc.perform(patch("/api/schools/{id}/coordinator", schoolId)
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType("application/json").content("{\"coordinatorId\":" + coordinatorId + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private School createSchool(String name) {
        var value = new School();
        value.setNome(name); value.setLogradouro("Rua A"); value.setBairro("Centro"); value.setCidade("Recife");
        return schools.saveAndFlush(value);
    }

    private SchoolUser createUser(String name, Role role) {
        var value = new SchoolUser();
        value.setFullName(name); value.setRa(name); value.setEmail(name + "@management.test");
        value.setRole(role); value.setPasswordHash("hash");
        return users.saveAndFlush(value);
    }
}

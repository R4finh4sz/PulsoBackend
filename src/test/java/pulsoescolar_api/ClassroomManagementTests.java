package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ClassroomManagementTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ClassroomRepository classrooms;
    MockMvc mvc;
    Classroom room;

    SchoolUser create(String name, Role role) {
        var user = new SchoolUser();
        user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("unused"); user.setRole(role);
        return users.saveAndFlush(user);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        create("manager", Role.PEDAGOGICAL_COORDINATOR);
        var teacher = create("teacher", Role.TEACHER);
        create("outsider", Role.TEACHER);
        room = new Classroom(); room.setName("3 ano"); room.setIdentifier("A");
        room.getTeachers().add(teacher); classrooms.saveAndFlush(room);
        create("student", Role.STUDENT).setClassroom(room);
    }

    @Test void readsDetailsAndTeacherRosterWithScopedAccess() throws Exception {
        String path = "/api/classrooms/" + room.getId();
        mvc.perform(get(path).with(user("student@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identifier").value("A"));
        mvc.perform(get(path + "/teachers").with(user("teacher@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].email").value("teacher@example.com"));
        mvc.perform(get(path + "/teachers").with(user("student@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("outsider@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/classrooms/999999").with(user("manager@example.com"))).andExpect(status().isNotFound());
    }

    @Test void patchesOneFieldAndEnforcesValidationAndPermissions() throws Exception {
        String path = "/api/classrooms/" + room.getId();
        mvc.perform(patch(path).with(user("manager@example.com")).with(csrf())
                .contentType("application/json").content("{\"identifier\":\"B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("3 ano"))
                .andExpect(jsonPath("$.identifier").value("B"));
        mvc.perform(patch(path).with(user("manager@example.com")).with(csrf())
                .contentType("application/json").content("{\"identifier\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path).with(user("teacher@example.com")).with(csrf())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch(path).with(user("manager@example.com"))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }

    @Test void rejectsDuplicateClassroom() throws Exception {
        var other = new Classroom(); other.setName("3 ano"); other.setIdentifier("B");
        classrooms.saveAndFlush(other);
        mvc.perform(patch("/api/classrooms/" + room.getId()).with(user("manager@example.com")).with(csrf())
                .contentType("application/json").content("{\"identifier\":\"B\"}"))
                .andExpect(status().isConflict());
    }
}

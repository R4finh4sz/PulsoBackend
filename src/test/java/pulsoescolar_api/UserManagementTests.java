package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class UserManagementTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    MockMvc mvc;
    SchoolUser student;

    SchoolUser create(String name, Role role) {
        var user = new SchoolUser();
        user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("unused"); user.setRole(role);
        return users.saveAndFlush(user);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        create("manager", Role.PEDAGOGICAL_COORDINATOR);
        student = create("Alice", Role.STUDENT);
        create("teacher", Role.TEACHER);
    }

    @Test void listsOnlyRequestedRoleAndSupportsSearchAndPagination() throws Exception {
        mvc.perform(get("/api/students").param("q", "ali").param("unassigned", "true")
                .param("size", "1").with(user("manager@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(student.getId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/teachers").with(user("manager@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].role").value("TEACHER"));
        mvc.perform(get("/api/coordinators").with(user("manager@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/students").param("page", "-1").with(user("manager@example.com")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students").param("size", "101").with(user("manager@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test void updatesPartiallyAndRejectsInvalidOrConflictingData() throws Exception {
        String path = "/api/students/" + student.getId();
        mvc.perform(patch(path).with(user("manager@example.com"))
                .contentType("application/json").content("{\"fullName\":\"Alice Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.ra").value("Alice"));
        mvc.perform(get(path).with(user("manager@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Alice Updated"));
        mvc.perform(patch(path).with(user("manager@example.com"))
                .contentType("application/json").content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path).with(user("manager@example.com"))
                .contentType("application/json").content("{\"email\":\"teacher@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test void enforcesAuthorizationRoleAndExistence() throws Exception {
        mvc.perform(get("/api/students").with(user("teacher@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/students").with(user("Alice@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/teachers/" + student.getId()).with(user("manager@example.com")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students/999999").with(user("manager@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/students/" + student.getId()).with(user("teacher@example.com"))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
}

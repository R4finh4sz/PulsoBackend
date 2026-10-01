package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.repository.school.SchoolRepository;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class SchoolRegistrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;

    MockMvc mvc;
    static final String SCHOOL = """
            {"nome":"Escola A","cnpj":"12.345.678/0001-90","logradouro":"Rua A","bairro":"Centro","cidade":"Recife"}
            """;

    @BeforeEach void setup() {
        for (Role role : Role.values()) {
            var actor = new SchoolUser();
            actor.setFullName(role.name()); actor.setRa("school-test-" + role);
            actor.setEmail(role + "@school.test"); actor.setRole(role); actor.setPasswordHash("hash");
            users.saveAndFlush(actor);
        }
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void rejectsDuplicateCnpjWithDifferentFormatting() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL)).andExpect(status().isCreated());
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL.replace("12.345.678/0001-90", "12345678000190")))
                .andExpect(status().isConflict());
    }

    @Test void validatesAllFields() throws Exception {
        for (String value : new String[]{"Escola A", "12.345.678/0001-90", "Rua A", "Centro", "Recife"}) {
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content(SCHOOL.replace(value, " ")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void schoolsReturnOnlyTheirActiveCoordinator() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                        .contentType("application/json").content(SCHOOL))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.coordinator").value(org.hamcrest.Matchers.nullValue()));
        var school = schools.findAll().getFirst();
        var coordinator = users.findByEmail("PEDAGOGICAL_COORDINATOR@school.test").orElseThrow();
        coordinator.setSchool(school);
        users.saveAndFlush(coordinator);
        mvc.perform(get("/api/schools").with(user("ADMIN@school.test").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].coordinator.id").value(coordinator.getId()))
                .andExpect(jsonPath("$[0].coordinator.fullName").value(coordinator.getFullName()))
                .andExpect(jsonPath("$[0].coordinator.email").value(coordinator.getEmail()))
                .andExpect(jsonPath("$[0].coordinator.passwordHash").doesNotExist());
        coordinator.setDeletedAt(java.time.Instant.now());
        users.saveAndFlush(coordinator);
        mvc.perform(get("/api/schools").with(user("ADMIN@school.test").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].coordinator").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void onlyAdminCanManageSchools() throws Exception {
        for (Role role : new Role[]{Role.STUDENT, Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR}) {
            mvc.perform(post("/api/schools").with(user(role + "@school.test").roles(role.name()))
                    .contentType("application/json").content(SCHOOL)).andExpect(status().isForbidden());
            mvc.perform(get("/api/schools").with(user(role + "@school.test").roles(role.name())))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/schools")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/schools")
                .contentType("application/json").content(SCHOOL)).andExpect(status().isUnauthorized());
    }
}

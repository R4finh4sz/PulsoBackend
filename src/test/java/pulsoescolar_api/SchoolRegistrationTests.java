package pulsoescolar_api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.service.mail.WelcomeMailService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class SchoolRegistrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @MockitoBean WelcomeMailService mail;
    MockMvc mvc;
    static final String SCHOOL = """
            {"nome":"Escola A","cnpj":"12.345.678/0001-90","logradouro":"Rua A","bairro":"Centro","cidade":"Recife"}
            """;
    static final String COORDINATOR = """
            {"fullName":"Ana Silva","ra":"ANA","email":"ana@example.com"%s}
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

    private Long linkCoordinator() {
        var school = new pulsoescolar_api.entity.school.School();
        school.setNome("Escola"); school.setCnpj("12345678000190");
        school.setLogradouro("Rua A"); school.setBairro("Centro"); school.setCidade("Recife");
        schools.saveAndFlush(school);
        var coordinator = users.findByEmail("PEDAGOGICAL_COORDINATOR@school.test").orElseThrow();
        coordinator.setSchool(school);
        users.saveAndFlush(coordinator);
        return school.getId();
    }

    @Test void teachersAndStudentsInheritCoordinatorSchool() throws Exception {
        Long schoolId = linkCoordinator();
        for (String route : new String[]{"teachers", "students"}) {
            String body = COORDINATOR.formatted("").replace("ANA", route).replace("ana@example.com", route + "@example.com");
            mvc.perform(post("/api/" + route).with(user("PEDAGOGICAL_COORDINATOR@school.test")).with(csrf())
                    .contentType("application/json").content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.schoolId").value(schoolId));
            assertEquals(schoolId, users.findByEmail(route + "@example.com").orElseThrow().getSchool().getId());
        }
    }

    @Test void coordinatorCannotChooseAnotherSchool() throws Exception {
        Long schoolId = linkCoordinator();
        for (String route : new String[]{"teachers", "students"}) {
            mvc.perform(post("/api/" + route).with(user("PEDAGOGICAL_COORDINATOR@school.test")).with(csrf())
                    .contentType("application/json").content(COORDINATOR.formatted(",\"schoolId\":" + (schoolId + 1))))
                    .andExpect(status().isBadRequest());
        }
        assertTrue(users.findByEmail("ana@example.com").isEmpty());
        verifyNoInteractions(mail);
    }

    @Test void coordinatorWithoutSchoolCannotRegisterTeachersOrStudents() throws Exception {
        for (String route : new String[]{"teachers", "students"}) {
            mvc.perform(post("/api/" + route).with(user("PEDAGOGICAL_COORDINATOR@school.test")).with(csrf())
                    .contentType("application/json").content(COORDINATOR.formatted("")))
                    .andExpect(status().isBadRequest());
        }
        assertTrue(users.findByEmail("ana@example.com").isEmpty());
        verifyNoInteractions(mail);
    }

    @Test void adminMustChooseExistingSchoolForTeachersAndStudents() throws Exception {
        Long schoolId = linkCoordinator();
        for (String route : new String[]{"teachers", "students"}) {
            for (String value : new String[]{"", ",\"schoolId\":999999"}) {
                mvc.perform(post("/api/" + route).with(user("ADMIN@school.test")).with(csrf())
                        .contentType("application/json").content(COORDINATOR.formatted(value)))
                        .andExpect(status().is(value.isEmpty() ? 400 : 404));
            }
            String body = COORDINATOR.formatted(",\"schoolId\":" + schoolId)
                    .replace("ANA", route).replace("ana@example.com", route + "@example.com");
            mvc.perform(post("/api/" + route).with(user("ADMIN@school.test")).with(csrf())
                    .contentType("application/json").content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.schoolId").value(schoolId));
        }
    }

    @Test void createsListsAndLinksSchool() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test")).with(csrf())
                .contentType("application/json").content(SCHOOL))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cnpj").value("12345678000190"));
        long id = schools.findAll().getFirst().getId();
        mvc.perform(get("/api/schools").with(user("ADMIN@school.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(post("/api/coordinators").with(user("ADMIN@school.test")).with(csrf())
                .contentType("application/json").content(COORDINATOR.formatted(",\"schoolId\":" + id)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.schoolId").value(id));
        assertEquals(id, users.findByEmail("ana@example.com").orElseThrow().getSchool().getId());
    }

    @Test void rejectsMissingInvalidAndUnknownSchoolBeforeSendingMail() throws Exception {
        for (String value : new String[]{"", ",\"schoolId\":null", ",\"schoolId\":0", ",\"schoolId\":-1"}) {
            mvc.perform(post("/api/coordinators").with(user("ADMIN@school.test")).with(csrf())
                    .contentType("application/json").content(COORDINATOR.formatted(value)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/coordinators").with(user("ADMIN@school.test")).with(csrf())
                .contentType("application/json").content(COORDINATOR.formatted(",\"schoolId\":999999")))
                .andExpect(status().isNotFound());
        assertTrue(users.findByEmail("ana@example.com").isEmpty());
        verifyNoInteractions(mail);
    }

    @Test void rejectsDuplicateCnpjWithDifferentFormatting() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test")).with(csrf())
                .contentType("application/json").content(SCHOOL)).andExpect(status().isCreated());
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test")).with(csrf())
                .contentType("application/json").content(SCHOOL.replace("12.345.678/0001-90", "12345678000190")))
                .andExpect(status().isConflict());
    }

    @Test void validatesAllFields() throws Exception {
        for (String value : new String[]{"Escola A", "12.345.678/0001-90", "Rua A", "Centro", "Recife"}) {
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test")).with(csrf())
                    .contentType("application/json").content(SCHOOL.replace(value, " ")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void onlyAdminCanManageSchoolsAndCreateCoordinators() throws Exception {
        for (Role role : new Role[]{Role.STUDENT, Role.TEACHER, Role.PEDAGOGICAL_COORDINATOR}) {
            mvc.perform(post("/api/schools").with(user(role + "@school.test")).with(csrf())
                    .contentType("application/json").content(SCHOOL)).andExpect(status().isForbidden());
            mvc.perform(get("/api/schools").with(user(role + "@school.test")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/coordinators").with(user(role + "@school.test")).with(csrf())
                    .contentType("application/json").content(COORDINATOR.formatted(",\"schoolId\":1")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/schools")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test"))
                .contentType("application/json").content(SCHOOL)).andExpect(status().isForbidden());
    }
}

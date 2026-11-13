package pulsoescolar_api.integration.school;

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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    pulsoescolar_api.service.school.SchoolAddressValidator addressValidator;

    MockMvc mvc;
    static final String SCHOOL = """
            {"nome":"Escola A","logradouro":"Rua A","bairro":"Centro","cidade":"Recife","cep":"50000-000","uf":"PE"}
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

    @Test void createsSchoolsWithAddressCepAndState() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Escola A"));
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL.replace("Escola A", "Escola B")))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(2L, schools.count());
        org.mockito.Mockito.verify(addressValidator, org.mockito.Mockito.times(2))
                .validate("50000-000", pulsoescolar_api.dto.school.BrazilianState.PE, "Recife", "Centro", "Rua A");
    }

    @Test void requiresCepAndState() throws Exception {
        for (String field : new String[]{",\"cep\":\"50000-000\"", ",\"uf\":\"PE\""}) {
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content(SCHOOL.replace(field, "")))
                    .andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(addressValidator);
    }

    @Test void rejectsInconsistentAddressesAndLookupFailuresWithoutSaving() throws Exception {
        for (RuntimeException failure : new RuntimeException[]{
                new pulsoescolar_api.exception.common.BusinessValidationException("Endereço divergente."),
                new pulsoescolar_api.exception.common.AddressLookupUnavailableException(null)}) {
            org.mockito.Mockito.doThrow(failure).when(addressValidator)
                    .validate("50000-000", pulsoescolar_api.dto.school.BrazilianState.PE, "Recife", "Centro", "Rua A");
            int expected = failure instanceof pulsoescolar_api.exception.common.BusinessValidationException ? 400 : 503;
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content(SCHOOL)).andExpect(status().is(expected));
            org.junit.jupiter.api.Assertions.assertEquals(0L, schools.count());
        }
    }

    @Test void locationUpdateComparesStoredAddressBeforeChangingCep() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL)).andExpect(status().isCreated());
        var school = schools.findAll().getFirst();
        org.mockito.Mockito.doThrow(new pulsoescolar_api.exception.common.BusinessValidationException("Endereço divergente."))
                .when(addressValidator).validate("01001-000", pulsoescolar_api.dto.school.BrazilianState.SP,
                        "Recife", "Centro", "Rua A");
        mvc.perform(patch("/api/schools/{id}/location", school.getId()).with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content("{\"cep\":\"01001-000\",\"uf\":\"SP\"}"))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals("50000000", school.getCep());
        org.junit.jupiter.api.Assertions.assertEquals("PE", school.getUf());
    }

    @Test void validatesAllFields() throws Exception {
        for (String value : new String[]{"Escola A", "Rua A", "Centro", "Recife"}) {
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content(SCHOOL.replace(value, " ")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void stateMustBeOneOfTheExactDropdownCodesOnCreateUpdateAndSearch() throws Exception {
        mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content(SCHOOL))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.uf").value("PE"));
        Long id = schools.findAll().getFirst().getId();
        for (String invalid : new String[]{"\"ZZ\"", "\"pe\"", "\" PE \"", "\"Pernambuco\"", "0", "\"0\"", "true"}) {
            mvc.perform(post("/api/schools").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content(SCHOOL.replace("\"uf\":\"PE\"", "\"uf\":" + invalid)))
                    .andExpect(status().isBadRequest());
            mvc.perform(patch("/api/schools/{id}/location", id).with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content("{\"cep\":\"50000-000\",\"uf\":" + invalid + "}"))
                    .andExpect(status().isBadRequest());
        }
        for (String invalid : new String[]{"ZZ", "pe", "Pernambuco", "0"}) {
            mvc.perform(get("/api/schools/search").param("state", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/schools/search").param("state", "PE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, schools.count());
    }

    @Test void enumChoicesCannotBeSubmittedAsNumbersThroughHttp() throws Exception {
        for (String invalid : new String[]{"1", "\"1\"", "\"teacher\"", "\" TEACHER \""}) {
            mvc.perform(post("/api/faqs").with(user("ADMIN@school.test").roles("ADMIN"))
                    .contentType("application/json").content("{\"title\":\"Aviso\",\"message\":\"Mensagem\",\"targetRole\":" + invalid + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/registration-requests/999999").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content("{\"status\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/registration-requests/999999").with(user("ADMIN@school.test").roles("ADMIN"))
                .contentType("application/json").content("{\"status\":\"APPROVED\",\"type\":1}"))
                .andExpect(status().isBadRequest());
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

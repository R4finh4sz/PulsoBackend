package pulsoescolar_api.integration.user;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import pulsoescolar_api.dto.faq.FaqResponse;
import pulsoescolar_api.entity.faq.Faq;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.faq.FaqRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FaqIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired FaqRepository faqs;
    @Autowired ObjectMapper json;
    MockMvc mvc;
    Map<Role, SchoolUser> accounts = new EnumMap<>(Role.class);

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (var role : Role.values()) {
            var account = new SchoolUser();
            var id = UUID.randomUUID().toString();
            account.setFullName(id); account.setRa(id); account.setEmail(id + "@faq.test");
            account.setPasswordHash("unused"); account.setRole(role);
            em.persist(account); accounts.put(role, account);
        }
    }

    @Test void adminCanCreateEditChangeAudienceAndDelete() throws Exception {
        var result = mvc.perform(post("/api/faqs").with(actor(Role.ADMIN)).contentType("application/json")
                        .content(body(" Título ", "TEACHER", " Mensagem ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Título"))
                .andExpect(jsonPath("$.message").value("Mensagem")).andReturn();
        var created = json.readValue(result.getResponse().getContentAsString(), FaqResponse.class);
        assertThat(faqs.findById(created.id()).orElseThrow().getTargetRole()).isEqualTo(Role.TEACHER);
        mvc.perform(get("/api/faqs").with(actor(Role.TEACHER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(created.id()));
        mvc.perform(put("/api/faqs/" + created.id()).with(actor(Role.ADMIN)).contentType("application/json")
                        .content(body("Novo título", "STUDENT", "Nova mensagem")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetRole").value("STUDENT"))
                .andExpect(jsonPath("$.title").value("Novo título")).andExpect(jsonPath("$.message").value("Nova mensagem"));
        mvc.perform(get("/api/faqs").with(actor(Role.TEACHER))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/faqs").with(actor(Role.STUDENT))).andExpect(jsonPath("$[0].id").value(created.id()));
        mvc.perform(delete("/api/faqs/" + created.id()).with(actor(Role.ADMIN))).andExpect(status().isNoContent());
        assertThat(faqs.findById(created.id())).isEmpty();
        mvc.perform(delete("/api/faqs/" + created.id()).with(actor(Role.ADMIN))).andExpect(status().isNotFound());
        mvc.perform(put("/api/faqs/" + created.id()).with(actor(Role.ADMIN)).contentType("application/json")
                .content(body("Título", "TEACHER", "Mensagem"))).andExpect(status().isNotFound());
    }

    @Test void readsAreFilteredByTokenAndOtherProfilesCannotWrite() throws Exception {
        for (var role : List.of(Role.TEACHER, Role.STUDENT, Role.PEDAGOGICAL_COORDINATOR)) {
            var faq = new Faq(); faq.setTitle(role.name()); faq.setTargetRole(role); faq.setMessage("Mensagem");
            faqs.saveAndFlush(faq);
        }
        var id = faqs.findAll().getFirst().getId();
        mvc.perform(get("/api/faqs").with(actor(Role.ADMIN))).andExpect(jsonPath("$.length()").value(3));
        for (var role : List.of(Role.TEACHER, Role.STUDENT, Role.PEDAGOGICAL_COORDINATOR)) {
            mvc.perform(get("/api/faqs?targetRole=ADMIN").with(actor(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].targetRole").value(role.name()));
            mvc.perform(post("/api/faqs").with(actor(role)).contentType("application/json")
                    .content(body("Título", "TEACHER", "Mensagem"))).andExpect(status().isForbidden());
            mvc.perform(put("/api/faqs/" + id).with(actor(role)).contentType("application/json")
                    .content(body("Título", "TEACHER", "Mensagem"))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/faqs/" + id).with(actor(role))).andExpect(status().isForbidden());
        }
        assertThat(faqs.count()).isEqualTo(3);
    }

    @Test void rejectsInvalidContentForCreateAndUpdate() throws Exception {
        var faq = new Faq(); faq.setTitle("Original"); faq.setTargetRole(Role.STUDENT); faq.setMessage("Original");
        faqs.saveAndFlush(faq);
        for (var body : List.of("{}", body(" ", "TEACHER", "Mensagem"), body("Título", "TEACHER", " "),
                body("Título", "ADMIN", "Mensagem"), body("Título", "UNKNOWN", "Mensagem"),
                body("x".repeat(201), "STUDENT", "Mensagem"), body("Título", "STUDENT", "x".repeat(5001)),
                "{\"title\":\"Título\",\"message\":\"Mensagem\"}")) {
            mvc.perform(post("/api/faqs").with(actor(Role.ADMIN)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/faqs/" + faq.getId()).with(actor(Role.ADMIN)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(faqs.count()).isEqualTo(1);
        assertThat(faqs.findById(faq.getId()).orElseThrow().getTitle()).isEqualTo("Original");
    }

    @Test void requiresAuthenticationAndCompletedTwoFactor() throws Exception {
        mvc.perform(get("/api/faqs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/faqs").contentType("application/json").content(body("Título", "STUDENT", "Mensagem")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/faqs").with(user("pending").authorities(() -> "TWO_FACTOR_PENDING")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/faqs").with(user("pending").authorities(() -> "TWO_FACTOR_PENDING"))
                .contentType("application/json").content(body("Título", "STUDENT", "Mensagem")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor actor(Role role) {
        return user(accounts.get(role).getEmail()).roles(role.name());
    }

    private String body(String title, String role, String message) {
        return json.writeValueAsString(Map.of("title", title, "targetRole", role, "message", message));
    }
}

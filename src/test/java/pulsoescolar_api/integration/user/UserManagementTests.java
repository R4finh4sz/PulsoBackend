package pulsoescolar_api.integration.user;

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
    @Autowired pulsoescolar_api.repository.school.SchoolRepository schools;
    @Autowired pulsoescolar_api.repository.classroom.ClassroomRepository classrooms;
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
        var school = new pulsoescolar_api.entity.school.School();
        school.setNome("Escola"); school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        schools.saveAndFlush(school);
        create("manager", Role.PEDAGOGICAL_COORDINATOR).setSchool(school);
        student = create("Alice", Role.STUDENT);
        student.setSchool(school);
        create("teacher", Role.TEACHER).setSchool(school);
        users.flush();
    }

    @Test void listsOnlyRequestedRoleAndSupportsSearchAndPagination() throws Exception {
        mvc.perform(get("/api/students").param("q", "ali").param("unassigned", "true")
                .param("size", "1").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(student.getId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/teachers").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].role").value("TEACHER"));
        mvc.perform(get("/api/coordinators").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/students").param("page", "-1").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students").param("size", "101").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isBadRequest());
    }

    @Test void updatesPartiallyAndRejectsInvalidOrConflictingData() throws Exception {
        String path = "/api/students/" + student.getId();
        mvc.perform(patch("/api/me").with(user(student.getEmail()).roles("STUDENT"))
                .contentType("application/json").content("{\"email\":\"updated@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.ra").value("Alice"));
        mvc.perform(get(path).with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Alice"));
        mvc.perform(patch("/api/me").with(user(student.getEmail()).roles("STUDENT"))
                .contentType("application/json").content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/me").with(user(student.getEmail()).roles("STUDENT"))
                .contentType("application/json").content("{\"email\":\"teacher@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test void eachRoleCanUpdateOnlyItsOwnProfile() throws Exception {
        for (var role : Role.values()) {
            var account = create("self" + role.name(), role);
            mvc.perform(patch("/api/me?userId=" + student.getId())
                    .with(user(account.getEmail()).roles(role.name()))
                    .contentType("application/json").content("{\"email\":\"updated" + role.name() + "@example.com\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(account.getId()))
                    .andExpect(jsonPath("$.fullName").value("self" + role.name()));
        }
        org.junit.jupiter.api.Assertions.assertEquals("Alice", users.findById(student.getId()).orElseThrow().getFullName());
        mvc.perform(patch("/api/me").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void managersCannotEditOtherStudentsOrTeachers() throws Exception {
        var admin = create("admin", Role.ADMIN);
        var teacher = users.findByEmail("teacher@example.com").orElseThrow();
        for (var actor : java.util.List.of(admin, users.findByEmail("manager@example.com").orElseThrow())) {
            for (String path : java.util.List.of("/api/students/" + student.getId(), "/api/teachers/" + teacher.getId())) {
                mvc.perform(patch(path).with(user(actor.getEmail()).roles(actor.getRole().name()))
                        .contentType("application/json").content("{\"email\":\"changed@example.com\"}"))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(patch("/api/teachers/" + teacher.getId()).with(user(teacher.getEmail()).roles("TEACHER"))
                .contentType("application/json").content("{\"email\":\"teacher.self@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("teacher.self@example.com"));
    }

    @Test void rejectsNameAndRaChangesOnAllProfileRoutes() throws Exception {
        for (String path : java.util.List.of("/api/me", "/api/students/" + student.getId())) {
            for (String field : java.util.List.of("fullName", "ra")) {
                mvc.perform(patch(path).with(user(student.getEmail()).roles("STUDENT"))
                        .contentType("application/json").content("{\"" + field + "\":\"Changed\"}"))
                        .andExpect(status().isBadRequest());
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals("Alice", student.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals("Alice", student.getRa());
    }

    @Test void updatesOnlyOwnPhotoAndRejectsInvalidImages() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        var photo = new org.springframework.mock.web.MockMultipartFile("photo", "profile.png", "image/png", output.toByteArray());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/me/photo?userId=" + student.getId())
                .file(photo).with(user("teacher@example.com").roles("TEACHER")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/photo").with(user("teacher@example.com").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(content().bytes(output.toByteArray()));
        org.junit.jupiter.api.Assertions.assertNull(student.getProfilePhoto());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/me/photo").file(
                new org.springframework.mock.web.MockMultipartFile("photo", "bad.png", "image/png", new byte[]{1, 2}))
                .with(user(student.getEmail()).roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/me/photo").file(photo))
                .andExpect(status().isUnauthorized());
    }

    @Test void enforcesAuthorizationRoleAndExistence() throws Exception {
        mvc.perform(get("/api/students").with(user("teacher@example.com").roles("TEACHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/students").with(user("Alice@example.com").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/teachers/" + student.getId()).with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students/999999").with(user("manager@example.com").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/students/" + student.getId()).with(user("teacher@example.com").roles("TEACHER"))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }

    @Test void administratorsCannotManageClassroomsAndUnassignedCoordinatorsCannotReadUsers() throws Exception {
        var admin = create("academicAdmin", Role.ADMIN);
        var coordinator = create("unassignedCoordinator", Role.PEDAGOGICAL_COORDINATOR);
        var room = new pulsoescolar_api.entity.classroom.Classroom();
        room.setName("1º ano · Manhã"); room.setIdentifier("Z"); room.setSchool(student.getSchool());
        classrooms.saveAndFlush(room);
        var actor = user(admin.getEmail()).roles("ADMIN");
        String path = "/api/classrooms/" + room.getId();
        for (String url : java.util.List.of("/api/classrooms", path, path + "/students", path + "/teachers")) {
            mvc.perform(get(url).with(actor)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/classrooms").with(actor).contentType("application/json")
                .content("{\"name\":\"2º ano · Tarde\",\"identifier\":\"Y\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).with(actor).contentType("application/json").content("{\"identifier\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path).with(actor)).andExpect(status().isForbidden());
        for (String target : java.util.List.of("students/" + student.getId(),
                "teachers/" + users.findByEmail("teacher@example.com").orElseThrow().getId())) {
            mvc.perform(put(path + "/" + target).with(actor)).andExpect(status().isForbidden());
            mvc.perform(delete(path + "/" + target).with(actor)).andExpect(status().isForbidden());
        }
        var unassigned = user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        for (String url : java.util.List.of("/api/students", "/api/teachers", "/api/coordinators",
                "/api/students/" + student.getId(), "/api/classrooms")) {
            mvc.perform(get(url).with(unassigned)).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/me").with(user(student.getEmail()).roles("STUDENT"))
                .contentType("application/json").content("{\"email\":\"" + "a".repeat(51) + "@example.com\"}"))
                .andExpect(status().isOk());
    }
}

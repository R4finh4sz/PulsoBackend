package pulsoescolar_api.integration.user;

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
import pulsoescolar_api.repository.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ActiveSchoolTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired ClassroomRepository classrooms;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    School first;
    School second;
    SchoolUser teacher;
    Classroom firstRoom;
    Classroom secondRoom;

    School school(String cnpj) {
        var school = new School(); school.setNome(cnpj); school.setCnpj(cnpj);
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    SchoolUser account(String name, Role role) {
        var account = new SchoolUser(); account.setFullName(name); account.setRa(name);
        account.setEmail(name + "@active.test"); account.setPasswordHash("unused"); account.setRole(role);
        account.setSchool(first); account.getSchools().add(second);
        return users.saveAndFlush(account);
    }

    Classroom room(String name, School school) {
        var room = new Classroom(); room.setName(name); room.setIdentifier("A"); room.setSchool(school);
        room.getTeachers().add(teacher);
        return classrooms.saveAndFlush(room);
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        first = school("11111111111111"); second = school("22222222222222");
        teacher = account("teacher", Role.TEACHER);
        firstRoom = room("Primeira", first); secondRoom = room("Segunda", second);
    }

    @Test
    void switchPersistsAndChangesClassroomContextWithoutRemovingMemberships() throws Exception {
        var actor = user(teacher.getEmail()).roles("TEACHER");
        mvc.perform(get("/api/teacher/classrooms").with(actor))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(firstRoom.getId()));
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolId").value(second.getId()));
        entityManager.flush(); entityManager.clear();
        var saved = users.findById(teacher.getId()).orElseThrow();
        assertThat(saved.getSchool().getId()).isEqualTo(second.getId());
        assertThat(saved.getEnrollmentSchool().getId()).isEqualTo(first.getId());
        assertThat(saved.getSchools()).hasSize(2);
        mvc.perform(get("/api/teacher/classrooms").with(actor))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(secondRoom.getId()));
        mvc.perform(get("/api/classrooms/" + firstRoom.getId()).with(actor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/classrooms/" + secondRoom.getId()).with(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile").with(actor)).andExpect(jsonPath("$.schools.length()").value(2))
                .andExpect(jsonPath("$.classrooms[0].id").value(secondRoom.getId()));
        mvc.perform(get("/api/me/export").with(actor)).andExpect(jsonPath("$.schools.length()").value(2))
                .andExpect(jsonPath("$.classrooms[0].id").value(secondRoom.getId()));
    }

    @Test
    void cannotChooseUnlinkedSchoolOrChangeAnotherUser() throws Exception {
        var outsider = school("33333333333333");
        var other = account("other", Role.TEACHER);
        var actor = user(teacher.getEmail()).roles("TEACHER");
        for (long id : new long[] {outsider.getId(), Long.MAX_VALUE}) {
            mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                    .content("{\"schoolId\":" + id + "}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(teacher.getSchool().getId()).isEqualTo(first.getId());
        mvc.perform(patch("/api/me/active-school?userId=" + other.getId()).with(actor).contentType("application/json")
                .content("{\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(teacher.getId()));
        assertThat(other.getSchool().getId()).isEqualTo(first.getId());
    }

    @Test
    void requestRequiresAuthenticationAndPositiveSchoolId() throws Exception {
        mvc.perform(patch("/api/me/active-school").contentType("application/json")
                .content("{\"schoolId\":1}")).andExpect(status().isUnauthorized());
        for (String body : new String[] {"{}", "{\"schoolId\":null}", "{\"schoolId\":0}", "{\"schoolId\":-1}"}) {
            mvc.perform(patch("/api/me/active-school").with(user(teacher.getEmail()).roles("TEACHER"))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void coordinatorContextScopesUsersAndEnrollmentOperations() throws Exception {
        var coordinator = account("coordinator", Role.PEDAGOGICAL_COORDINATOR);
        coordinator.setActiveSchool(second);
        var actor = user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(get("/api/teachers").with(actor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(teacher.getId()));
        mvc.perform(put("/api/classrooms/" + firstRoom.getId() + "/teachers/" + teacher.getId()).with(actor))
                .andExpect(status().isForbidden());
        var student = account("student", Role.STUDENT);
        mvc.perform(put("/api/classrooms/" + firstRoom.getId() + "/students/" + student.getId()).with(actor))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/classrooms/" + secondRoom.getId() + "/students/" + student.getId()).with(actor))
                .andExpect(status().isOk());
    }

    @Test
    void switchingStudentSchoolHidesClassroomWithoutRemovingEnrollment() throws Exception {
        var student = account("student", Role.STUDENT); student.setClassroom(firstRoom);
        var actor = user(student.getEmail()).roles("STUDENT");
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classroomId").doesNotExist());
        assertThat(student.getClassroom()).isEqualTo(firstRoom);
        mvc.perform(get("/api/classrooms").with(actor)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/classrooms/" + firstRoom.getId()).with(actor)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + first.getId() + "}"))
                .andExpect(jsonPath("$.classroomId").value(firstRoom.getId()));
    }
}

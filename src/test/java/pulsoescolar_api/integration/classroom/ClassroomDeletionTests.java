package pulsoescolar_api.integration.classroom;

import java.time.Instant;
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
import pulsoescolar_api.entity.subject.*;
import pulsoescolar_api.entity.notification.UserNotification;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.subject.*;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ClassroomDeletionTests {
    @Autowired WebApplicationContext context;
    @Autowired ClassroomRepository classrooms;
    @Autowired UserRepository users;
    @Autowired SchoolRepository schools;
    @Autowired SubjectRepository subjects;
    @Autowired SubjectInvitationRepository invitations;
    @Autowired UserNotificationRepository notifications;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    Classroom room;
    School school;

    SchoolUser account(String name, Role role, School school) {
        var account = new SchoolUser();
        account.setFullName(name);
        account.setRa(name);
        account.setEmail(name + "@deletion.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setSchool(school);
        return users.saveAndFlush(account);
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        school = new School();
        school.setNome("Escola"); school.setCnpj("12345678901234");
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        schools.saveAndFlush(school);
        account("admin", Role.ADMIN, null);
        account("coordinator", Role.PEDAGOGICAL_COORDINATOR, school);
        account("unassigned", Role.PEDAGOGICAL_COORDINATOR, null);
        var teacher = account("teacher", Role.TEACHER, school);
        room = new Classroom(); room.setName("Turma"); room.setIdentifier("A"); room.setSchool(school);
        room.getTeachers().add(teacher);
        classrooms.saveAndFlush(room);
        account("student", Role.STUDENT, school).setClassroom(room);
    }

    String path() { return "/api/classrooms/" + room.getId(); }

    @Test
    void deletionPreservesHistoryAndHidesActiveLinks() throws Exception {
        var teacher = users.findByEmail("teacher@deletion.test").orElseThrow();
        var subject = new Subject(); subject.setName("Histórico"); subject.setClassroom(room); subject.setTeacher(teacher);
        subjects.saveAndFlush(subject);
        mvc.perform(delete(path()).with(user("coordinator@deletion.test").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(classrooms.findById(room.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(room.getTeachers()).contains(teacher);
        assertThat(subjects.findById(subject.getId())).isPresent();
        assertThat(users.findByEmail("student@deletion.test").orElseThrow().getClassroom().getId()).isEqualTo(room.getId());
        for (var role : Role.values()) {
            String name = switch (role) {
                case ADMIN -> "admin";
                case PEDAGOGICAL_COORDINATOR -> "coordinator";
                case TEACHER -> "teacher";
                case STUDENT -> "student";
            };
            var actor = user(name + "@deletion.test").roles(role.name());
            mvc.perform(get("/api/classrooms").with(actor)).andExpect(jsonPath("$").isEmpty());
            mvc.perform(get("/api/me/profile").with(actor)).andExpect(jsonPath("$.classrooms").isEmpty());
            mvc.perform(get(path()).with(actor)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/teacher/classrooms").with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/me").with(user("student@deletion.test").roles("STUDENT")))
                .andExpect(jsonPath("$.classroomId").doesNotExist());
        mvc.perform(get(path() + "/subjects/" + subject.getId()).with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch(path()).with(user("admin@deletion.test").roles("ADMIN"))
                .contentType("application/json").content("{\"name\":\"Outra\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(path() + "/teachers/" + teacher.getId()).with(user("admin@deletion.test").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path()).with(user("admin@deletion.test").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminOrCoordinatorFromSameSchoolCanDelete() throws Exception {
        mvc.perform(delete(path())).andExpect(status().isUnauthorized());
        for (var role : new Role[] {Role.TEACHER, Role.STUDENT}) {
            mvc.perform(delete(path()).with(user(role == Role.TEACHER ? "teacher@deletion.test" : "student@deletion.test")
                    .roles(role.name()))).andExpect(status().isForbidden());
        }
        mvc.perform(delete(path()).with(user("unassigned@deletion.test").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        var other = new School(); other.setNome("Outra"); other.setCnpj("22345678901234");
        other.setLogradouro("Rua"); other.setBairro("Centro"); other.setCidade("Cidade");
        schools.saveAndFlush(other);
        account("outsider", Role.PEDAGOGICAL_COORDINATOR, other);
        mvc.perform(delete(path()).with(user("outsider@deletion.test").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        assertThat(room.getDeletedAt()).isNull();
        mvc.perform(delete(path()).with(user("admin@deletion.test").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletedClassroomCannotReceiveStudentsOrAcceptInvitations() throws Exception {
        var teacher = users.findByEmail("teacher@deletion.test").orElseThrow();
        var guest = account("guest", Role.TEACHER, school);
        var subject = new Subject(); subject.setName("Disciplina"); subject.setClassroom(room); subject.setTeacher(teacher);
        subjects.saveAndFlush(subject);
        var notification = new UserNotification(); notification.setRecipient(guest); notification.setType("SUBJECT_INVITATION");
        notification.setTitle("Convite"); notification.setMessage("Convite"); notification.setCreatedAt(Instant.now());
        notifications.saveAndFlush(notification);
        var invitation = new SubjectInvitation(); invitation.setRecipient(guest); invitation.setInviter(teacher);
        invitation.setSubject(subject); invitation.setNotification(notification);
        invitation.setCreatedAt(Instant.now()); invitation.setStatus(SubjectInvitationStatus.PENDING);
        invitations.saveAndFlush(invitation);
        mvc.perform(delete(path()).with(user("admin@deletion.test").roles("ADMIN"))).andExpect(status().isNoContent());
        entityManager.flush();
        var student = users.findByEmail("student@deletion.test").orElseThrow();
        mvc.perform(put(path() + "/students/" + student.getId()).with(user("admin@deletion.test").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/subject-invitations/" + invitation.getId()).with(user(guest.getEmail()).roles("TEACHER"))
                .contentType("application/json").content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());
        assertThat(invitations.findById(invitation.getId())).isPresent();
        assertThat(invitation.getStatus()).isEqualTo(SubjectInvitationStatus.PENDING);
        assertThat(room.getTeachers()).doesNotContain(guest);
    }

    @Test
    void adminCanDeleteSchoollessClassroomAndUnknownIdReturns404() throws Exception {
        room.setSchool(null);
        mvc.perform(delete(path()).with(user("coordinator@deletion.test").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path()).with(user("admin@deletion.test").roles("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/classrooms/9223372036854775807").with(user("admin@deletion.test").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}

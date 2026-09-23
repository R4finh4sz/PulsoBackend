package pulsoescolar_api;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.subject.SubjectRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ClassroomUnlinkTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ClassroomRepository classrooms;
    @Autowired SubjectRepository subjects;
    @Autowired EntityManager entityManager;
    MockMvc mvc;
    Classroom room;
    SchoolUser student;
    SchoolUser teacher;

    SchoolUser create(String name, Role role) {
        var user = new SchoolUser();
        user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("unused"); user.setRole(role);
        return users.saveAndFlush(user);
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        create("manager", Role.PEDAGOGICAL_COORDINATOR);
        teacher = create("teacher", Role.TEACHER);
        student = create("student", Role.STUDENT);
        room = new Classroom(); room.setName("3 ano"); room.setIdentifier("A");
        room.getTeachers().add(teacher); classrooms.saveAndFlush(room);
        student.setClassroom(room);
    }

    @Test void removesStudentPersistentlyAndRevokesAccess() throws Exception {
        String path = "/api/classrooms/" + room.getId() + "/students/" + student.getId();
        mvc.perform(delete(path).with(user("manager@example.com"))).andExpect(status().isNoContent());
        entityManager.flush(); entityManager.clear();
        assertNull(users.findById(student.getId()).orElseThrow().getClassroom());
        mvc.perform(delete(path).with(user("manager@example.com"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/classrooms/" + room.getId() + "/subjects").with(user("student@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test void preservesOtherClassroomAndChecksPermissionsAndRoles() throws Exception {
        var other = new Classroom(); other.setName("4 ano"); other.setIdentifier("A");
        classrooms.saveAndFlush(other);
        String path = "/api/classrooms/" + room.getId() + "/students/" + student.getId();
        mvc.perform(delete(path).with(user("teacher@example.com"))).andExpect(status().isForbidden());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/classrooms/" + other.getId() + "/students/" + student.getId())
                .with(user("manager@example.com"))).andExpect(status().isNotFound());
        assertEquals(room.getId(), student.getClassroom().getId());
        mvc.perform(delete("/api/classrooms/" + room.getId() + "/students/" + teacher.getId())
                .with(user("manager@example.com"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/classrooms/999999/students/" + student.getId())
                .with(user("manager@example.com"))).andExpect(status().isNotFound());
    }

    @Test void removesTeacherPersistentlyAndRevokesAccess() throws Exception {
        String path = "/api/classrooms/" + room.getId() + "/teachers/" + teacher.getId();
        mvc.perform(delete(path).with(user("teacher@example.com"))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user("manager@example.com"))).andExpect(status().isNoContent());
        entityManager.flush(); entityManager.clear();
        assertTrue(classrooms.findById(room.getId()).orElseThrow().getTeachers().isEmpty());
        mvc.perform(delete(path).with(user("manager@example.com"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/classrooms/" + room.getId() + "/subjects").with(user("teacher@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test void preservesTeacherResponsibleForSubjects() throws Exception {
        var subject = new Subject(); subject.setName("Math"); subject.setClassroom(room); subject.setTeacher(teacher);
        subjects.saveAndFlush(subject);
        mvc.perform(delete("/api/classrooms/" + room.getId() + "/teachers/" + teacher.getId())
                .with(user("manager@example.com"))).andExpect(status().isConflict());
        entityManager.flush(); entityManager.clear();
        assertEquals(1, classrooms.findById(room.getId()).orElseThrow().getTeachers().size());
        assertTrue(subjects.existsById(subject.getId()));
    }
}

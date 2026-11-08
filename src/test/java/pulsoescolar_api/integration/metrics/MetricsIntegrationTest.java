package pulsoescolar_api.integration.metrics;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.activity.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.service.metrics.SchoolMetricsService;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:5173")
@Transactional
class MetricsIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired SchoolMetricsService schoolMetrics;

    @Test void routesRestrictRolesAndRetainCors() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String path : new String[]{"/api/admin/metrics", "/api/schools/me/metrics", "/api/schools/me/metrics/growth?year=2026"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user("pending").authorities(() -> "TWO_FACTOR_PENDING")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/metrics").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history").isArray())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/metrics").with(user("coord").roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/me/metrics").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/me/metrics/growth?year=2026").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void schoolCountsAreIsolatedAndExcludeDeletedRecords() throws Exception {
        var first = school("11111111000111");
        var second = school("22222222000122");
        var coordinator = person("coord-metrics", Role.PEDAGOGICAL_COORDINATOR, first);
        var otherCoordinator = person("other-coord-metrics", Role.PEDAGOGICAL_COORDINATOR, second);
        var student = person("student-metrics", Role.STUDENT, first);
        var deletedStudent = person("deleted-metrics", Role.STUDENT, first);
        deletedStudent.setDeletedAt(Instant.now());
        var teacher = person("teacher-metrics", Role.TEACHER, first);
        person("other-student-metrics", Role.STUDENT, second);
        var room = room(first, "Metrics room");
        room.getTeachers().add(teacher);
        student.setClassroom(room);
        em.flush();
        room(second, "Other room");
        var deletedRoom = room(first, "Deleted room");
        deletedRoom.setDeletedAt(Instant.now());
        var subject = new Subject(); subject.setName("Math"); subject.setClassroom(room); subject.setTeacher(teacher);
        em.persist(subject);
        var activity = new Activity(); activity.setSubject(subject); activity.setCreatedBy(teacher); activity.setCreatedAt(Instant.now());
        em.persist(activity);
        var submission = new QuizSubmission(); submission.setActivity(activity); submission.setStudent(student); submission.setSubmittedAt(Instant.now());
        em.persist(submission);
        var inactiveSubmission = new QuizSubmission();
        inactiveSubmission.setActivity(activity); inactiveSubmission.setStudent(deletedStudent);
        inactiveSubmission.setSubmittedAt(Instant.now());
        em.persist(inactiveSubmission);
        em.flush();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/schools/me/metrics").param("schoolId", second.getId().toString())
                .with(user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolId").value(first.getId()))
                .andExpect(jsonPath("$.bigNumbers.students").value(1)).andExpect(jsonPath("$.bigNumbers.teachers").value(1))
                .andExpect(jsonPath("$.bigNumbers.classrooms").value(1)).andExpect(jsonPath("$.bigNumbers.subjects").value(1))
                .andExpect(jsonPath("$.bigNumbers.activities").value(1)).andExpect(jsonPath("$.bigNumbers.submissions").value(1));
        mvc.perform(get("/api/schools/me/metrics").with(user(otherCoordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolId").value(second.getId()))
                .andExpect(jsonPath("$.bigNumbers.students").value(1)).andExpect(jsonPath("$.bigNumbers.activities").value(0));
        coordinator.setSchool(null); coordinator.setActiveSchool(null);
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(coordinator.getEmail(), ""));
            assertThatThrownBy(() -> schoolMetrics.get()).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void growthUsesLocalMonthsRequestedYearAndSchoolAndReportsUnknownDates() throws Exception {
        var first = school("33333333000133");
        var second = school("44444444000144");
        var coordinator = person("growth-coord", Role.PEDAGOGICAL_COORDINATOR, first);
        var autoDated = person("auto-date", Role.TEACHER, second);
        assertThat(autoDated.getCreatedAt()).isNotNull();
        datedPerson("jan-student", Role.STUDENT, first, "2027-02-01T02:59:59Z");
        datedPerson("feb-student", Role.STUDENT, first, "2027-02-01T03:00:00Z");
        datedPerson("jan-teacher", Role.TEACHER, first, "2027-01-01T03:00:00Z");
        datedPerson("previous-year", Role.STUDENT, first, "2027-01-01T02:59:59Z");
        datedPerson("next-year", Role.STUDENT, first, "2028-01-01T03:00:00Z");
        datedPerson("other-school", Role.STUDENT, second, "2027-01-15T12:00:00Z");
        var deleted = datedPerson("growth-deleted", Role.STUDENT, first, "2027-01-15T12:00:00Z");
        deleted.setDeletedAt(Instant.now());
        var legacy = person("legacy", Role.STUDENT, first);
        em.flush();
        em.createNativeQuery("update school_users set created_at = null where id = :id")
                .setParameter("id", legacy.getId()).executeUpdate();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var auth = user(coordinator.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(get("/api/schools/me/metrics/growth").param("year", "2027")
                .param("schoolId", second.getId().toString()).with(auth))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.schoolId").value(first.getId()))
                .andExpect(jsonPath("$.year").value(2027)).andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.months[0].students").value(1))
                .andExpect(jsonPath("$.months[0].teachers").value(1))
                .andExpect(jsonPath("$.months[1].students").value(1))
                .andExpect(jsonPath("$.months[1].cumulativeStudents").value(2))
                .andExpect(jsonPath("$.months[11].students").value(0))
                .andExpect(jsonPath("$.months[11].cumulativeStudents").value(2))
                .andExpect(jsonPath("$.months[11].cumulativeTeachers").value(1))
                .andExpect(jsonPath("$.averageMonthlyStudents").value(0.17))
                .andExpect(jsonPath("$.averageMonthlyTeachers").value(0.08))
                .andExpect(jsonPath("$.studentsWithoutRegistrationDate").value(1));
        mvc.perform(get("/api/schools/me/metrics/growth?year=2029").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.months[11].cumulativeStudents").value(0));
        for (String year : new String[]{"0", "10000", "abc"}) {
            mvc.perform(get("/api/schools/me/metrics/growth").param("year", year).with(auth))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/schools/me/metrics/growth").with(auth)).andExpect(status().isBadRequest());
        coordinator.setSchool(null); coordinator.setActiveSchool(null);
        mvc.perform(get("/api/schools/me/metrics/growth?year=2027").with(auth))
                .andExpect(status().isForbidden());
    }

    private SchoolUser datedPerson(String name, Role role, School school, String date) {
        var user = new SchoolUser(); user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("unused"); user.setRole(role); user.setSchool(school); user.setCreatedAt(Instant.parse(date));
        em.persist(user); return user;
    }

    private School school(String schoolName) {
        var school = new School(); school.setNome("School " + schoolName);
        school.setLogradouro("Rua A"); school.setBairro("Centro"); school.setCidade("Recife");
        em.persist(school); return school;
    }

    private SchoolUser person(String name, Role role, School school) {
        var user = new SchoolUser(); user.setFullName(name); user.setRa(name); user.setEmail(name + "@example.com");
        user.setPasswordHash("unused"); user.setRole(role); user.setSchool(school);
        em.persist(user); return user;
    }

    private Classroom room(School school, String name) {
        var room = new Classroom(); room.setSchool(school); room.setName(name); room.setIdentifier("A");
        em.persist(room); return room;
    }
}

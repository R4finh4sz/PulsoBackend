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
    @Autowired pulsoescolar_api.repository.user.StudentTransferRepository transfers;
    MockMvc mvc;
    School first;
    School second;
    SchoolUser teacher;
    Classroom firstRoom;
    Classroom secondRoom;

    School school(String schoolName) {
        var school = new School(); school.setNome(schoolName);
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    SchoolUser account(String name, Role role) {
        return account(name, role, first);
    }

    SchoolUser account(String name, Role role, School school) {
        var account = new SchoolUser(); account.setFullName(name); account.setRa(name);
        account.setEmail(name + "@active.test"); account.setPasswordHash("unused"); account.setRole(role);
        account.setSchool(school); account.getSchools().add(second);
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
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(firstRoom.getId()))
                .andExpect(jsonPath("$[0].schoolId").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(secondRoom.getId()))
                .andExpect(jsonPath("$[1].schoolName").value(second.getNome()));
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schoolId").value(second.getId()));
        entityManager.flush(); entityManager.clear();
        var saved = users.findById(teacher.getId()).orElseThrow();
        assertThat(saved.getSchool().getId()).isEqualTo(second.getId());
        assertThat(saved.getEnrollmentSchool().getId()).isEqualTo(first.getId());
        assertThat(saved.getSchools()).hasSize(2);
        mvc.perform(get("/api/teacher/classrooms").with(actor))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(firstRoom.getId()))
                .andExpect(jsonPath("$[1].id").value(secondRoom.getId()));
        mvc.perform(get("/api/classrooms").with(actor))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(secondRoom.getId()));
        mvc.perform(get("/api/classrooms/" + firstRoom.getId()).with(actor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/classrooms/" + secondRoom.getId()).with(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile").with(actor)).andExpect(jsonPath("$.schools.length()").value(2))
                .andExpect(jsonPath("$.classrooms[0].id").value(secondRoom.getId()));
        mvc.perform(get("/api/me/export").with(actor)).andExpect(jsonPath("$.schools").doesNotExist())
                .andExpect(jsonPath("$.classrooms").doesNotExist());
    }

    @Test
    void teacherClassroomsExcludeDeletedUnassignedAndUnauthorizedSchools() throws Exception {
        var foreign = school("33333333333333");
        room("Escola sem vínculo", foreign);
        var deleted = room("Excluída", first);
        deleted.setDeletedAt(java.time.Instant.now());
        var unassigned = room("Sem professor", first);
        unassigned.getTeachers().clear();
        mvc.perform(get("/api/teacher/classrooms").with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(firstRoom.getId()))
                .andExpect(jsonPath("$[1].id").value(secondRoom.getId()));
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
                .andExpect(status().isForbidden());
    }

    @Test
    void sharedIdsRequireTypeAndCannotReviewWrongRequest() throws Exception {
        var source = account("collision-source", Role.PEDAGOGICAL_COORDINATOR);
        var destination = account("collision-destination", Role.PEDAGOGICAL_COORDINATOR, second);
        var student = account("collision-student", Role.STUDENT);
        entityManager.persist(new pulsoescolar_api.entity.terms.TermsVersion(998L, "Termos", "Conteúdo"));
        var registration = new RegistrationRequest();
        registration.setFullName("Outro Aluno"); registration.setRa("collision-new");
        registration.setEmail("collision-new@active.test"); registration.setRole(Role.STUDENT);
        registration.setSchool(second); registration.setTermsVersion(998L); registration.setStatus(RegistrationStatus.PENDING);
        registration.setRequestedAt(java.time.Instant.now()); entityManager.persist(registration); entityManager.flush();
        entityManager.createNativeQuery("""
                insert into student_transfers (id, student_id, origin_id, destination_id, requested_by_id, requested_at, status)
                values (:id, :student, :origin, :destination, :actor, :now, 'PENDING')
                """).setParameter("id", registration.getId()).setParameter("student", student.getId())
                .setParameter("origin", first.getId()).setParameter("destination", second.getId())
                .setParameter("actor", source.getId()).setParameter("now", java.time.Instant.now()).executeUpdate();
        var actor = user(destination.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        String path = "/api/registration-requests/" + registration.getId();
        mvc.perform(patch(path).with(actor).contentType("application/json").content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isConflict());
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(transfers.findById(registration.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.PENDING);
        mvc.perform(patch(path).with(actor).contentType("application/json")
                .content("{\"type\":\"TRANSFER\",\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("TRANSFER"));
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    void transfersShareRegistrationInboxAndApprovalWithoutLosingStudentHistory() throws Exception {
        var source = account("unified-source", Role.PEDAGOGICAL_COORDINATOR);
        var destination = account("unified-destination", Role.PEDAGOGICAL_COORDINATOR, second);
        var student = account("unified-student", Role.STUDENT);
        student.setClassroom(firstRoom);
        student.setProfilePhoto(new byte[]{1, 2});
        var subject = new pulsoescolar_api.entity.subject.Subject();
        subject.setName("Matemática"); subject.setClassroom(firstRoom); subject.setTeacher(teacher);
        entityManager.persist(subject);
        var activity = new pulsoescolar_api.entity.activity.Activity();
        activity.setSubject(subject); activity.setCreatedBy(teacher); activity.setCreatedAt(java.time.Instant.now());
        for (int i = 0; i < 10; i++) {
            var question = new pulsoescolar_api.entity.activity.QuizQuestion();
            question.setText("Questão " + i); question.setTheme("Números");
            question.getOptions().addAll(java.util.List.of("A", "B", "C", "D"));
            activity.getQuestions().add(question);
        }
        entityManager.persist(activity);
        var submission = new pulsoescolar_api.entity.activity.QuizSubmission();
        submission.setActivity(activity); submission.setStudent(student); submission.setSubmittedAt(java.time.Instant.now());
        submission.setGradedAt(java.time.Instant.now()); submission.setGradedBy(teacher);
        submission.setCorrectAnswers(10); submission.setScore(new java.math.BigDecimal("100.00"));
        submission.getAnswers().addAll(java.util.Collections.nCopies(10, 0));
        entityManager.persist(submission);
        var term = new pulsoescolar_api.entity.terms.TermsVersion(999L, "Termos", "Conteúdo");
        entityManager.persist(term);
        var registration = new RegistrationRequest();
        registration.setFullName("Novo Aluno"); registration.setRa("unified-new");
        registration.setEmail("unified-new@active.test"); registration.setRole(Role.STUDENT);
        registration.setSchool(second); registration.setTermsVersion(999L); registration.setStatus(RegistrationStatus.PENDING);
        registration.setRequestedAt(java.time.Instant.now().minusSeconds(3600));
        entityManager.persist(registration); entityManager.flush();
        var sourceActor = user(source.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        var destinationActor = user(destination.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(post("/api/students/transfer").with(sourceActor).contentType("application/json")
                .content("{\"fullName\":\"unified-student\",\"ra\":\"unified-student\",\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isCreated());
        var transfer = transfers.findAll().getFirst();
        mvc.perform(get("/api/registration-requests").with(destinationActor).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("REGISTRATION"));
        mvc.perform(get("/api/registration-requests").with(destinationActor).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].id").value(transfer.getId()))
                .andExpect(jsonPath("$.content[0].userId").value(student.getId()))
                .andExpect(jsonPath("$.content[0].originSchoolId").value(first.getId()));
        mvc.perform(get("/api/registration-requests").with(destinationActor).param("type", "TRANSFER"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/registration-requests").with(destinationActor).param("role", "TEACHER"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/registration-requests").with(sourceActor))
                .andExpect(jsonPath("$.totalElements").value(0));
        String path = "/api/registration-requests/" + transfer.getId();
        mvc.perform(get(path + "/photo").with(destinationActor).param("type", "TRANSFER"))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2}));
        mvc.perform(get(path + "/photo").with(sourceActor).param("type", "TRANSFER"))
                .andExpect(status().isForbidden());
        String approval = "{\"type\":\"TRANSFER\",\"status\":\"APPROVED\",\"classroomId\":" + secondRoom.getId() + "}";
        mvc.perform(patch(path).with(sourceActor).contentType("application/json").content(approval))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).with(destinationActor).contentType("application/json")
                .content("{\"type\":\"TRANSFER\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());
        long userCount = users.count();
        mvc.perform(patch(path).with(destinationActor).contentType("application/json").content(approval))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(student.getSchool().getId()).isEqualTo(second.getId());
        assertThat(student.getClassroom().getId()).isEqualTo(secondRoom.getId());
        assertThat(student.getPasswordHash()).isEqualTo("unused");
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        mvc.perform(get("/api/student/activities").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].activity.id").value(activity.getId()))
                .andExpect(jsonPath("$[0].submission.score").value(100));
        mvc.perform(get("/api/submissions/{id}/result", submission.getId()).with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(100));
        mvc.perform(patch(path).with(destinationActor).contentType("application/json").content(approval))
                .andExpect(status().isConflict());
    }

    @Test
    void transferRequiresDestinationApprovalAndAClassroomInThatSchool() throws Exception {
        var source = account("source", Role.PEDAGOGICAL_COORDINATOR);
        var destination = account("destination", Role.PEDAGOGICAL_COORDINATOR, second);
        var student = account("student-transfer", Role.STUDENT);
        student.setClassroom(firstRoom);
        String body = "{\"fullName\":\"student-transfer\",\"ra\":\"student-transfer\",\"schoolId\":" + second.getId() + "}";
        mvc.perform(post("/api/students/transfer").with(user(source.getEmail()).roles("PEDAGOGICAL_COORDINATOR"))
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(student.getSchool()).isEqualTo(first);
        assertThat(student.getClassroom()).isEqualTo(firstRoom);
        mvc.perform(post("/api/students/transfer").with(user(source.getEmail()).roles("PEDAGOGICAL_COORDINATOR"))
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        var transfer = transfers.findAll().getFirst();
        var sourceActor = user(source.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        var destinationActor = user(destination.getEmail()).roles("PEDAGOGICAL_COORDINATOR");
        mvc.perform(get("/api/students/transfers").with(sourceActor)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/students/transfers").with(destinationActor))
                .andExpect(jsonPath("$[0].id").value(transfer.getId()));
        String path = "/api/students/transfers/" + transfer.getId();
        mvc.perform(patch(path).with(sourceActor).contentType("application/json")
                .content("{\"status\":\"APPROVED\",\"classroomId\":" + secondRoom.getId() + "}"))
                .andExpect(status().isForbidden());
        for (String invalid : new String[]{"{\"status\":\"APPROVED\"}",
                "{\"status\":\"APPROVED\",\"classroomId\":" + firstRoom.getId() + "}",
                "{\"status\":\"PENDING\"}"}) {
            mvc.perform(patch(path).with(destinationActor).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(student.getSchool()).isEqualTo(first);
        mvc.perform(patch(path).with(destinationActor).contentType("application/json")
                .content("{\"status\":\"APPROVED\",\"classroomId\":" + secondRoom.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        entityManager.flush(); entityManager.clear();
        var saved = users.findById(student.getId()).orElseThrow();
        assertThat(saved.getSchool().getId()).isEqualTo(second.getId());
        assertThat(saved.getClassroom().getId()).isEqualTo(secondRoom.getId());
        assertThat(saved.getSchools()).extracting(School::getId).containsExactly(second.getId());
        mvc.perform(patch(path).with(destinationActor).contentType("application/json")
                .content("{\"status\":\"REJECTED\"}")).andExpect(status().isConflict());
    }

    @Test
    void rejectedTransferKeepsOriginalSchoolAndClassroom() throws Exception {
        var source = account("source", Role.PEDAGOGICAL_COORDINATOR);
        var destination = account("destination", Role.PEDAGOGICAL_COORDINATOR, second);
        var student = account("student-transfer", Role.STUDENT);
        student.setClassroom(firstRoom);
        mvc.perform(post("/api/students/transfer").with(user(source.getEmail()).roles("PEDAGOGICAL_COORDINATOR"))
                .contentType("application/json").content("{\"fullName\":\"student-transfer\",\"ra\":\"student-transfer\",\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/students/transfers/" + transfers.findAll().getFirst().getId())
                .with(user(destination.getEmail()).roles("PEDAGOGICAL_COORDINATOR"))
                .contentType("application/json").content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(student.getSchool()).isEqualTo(first);
        assertThat(student.getClassroom()).isEqualTo(firstRoom);
    }

    @Test
    void studentAndTeacherCannotRequestOrReviewTransfers() throws Exception {
        var student = account("student-transfer", Role.STUDENT);
        for (var account : new SchoolUser[]{teacher, student}) {
            var actor = user(account.getEmail()).roles(account.getRole().name());
            mvc.perform(post("/api/students/transfer").with(actor).contentType("application/json")
                    .content("{\"fullName\":\"student-transfer\",\"ra\":\"student-transfer\",\"schoolId\":" + second.getId() + "}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/students/transfers").with(actor)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/students/transfers/1").with(actor).contentType("application/json")
                    .content("{\"status\":\"REJECTED\"}")).andExpect(status().isForbidden());
        }
    }

    @Test
    void studentCannotSwitchSchoolAndKeepsEnrollment() throws Exception {
        var student = account("student", Role.STUDENT); student.setClassroom(firstRoom);
        var actor = user(student.getEmail()).roles("STUDENT");
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + second.getId() + "}"))
                .andExpect(status().isForbidden());
        assertThat(student.getClassroom()).isEqualTo(firstRoom);
        mvc.perform(get("/api/classrooms/" + firstRoom.getId()).with(actor)).andExpect(status().isOk());
        mvc.perform(patch("/api/me/active-school").with(actor).contentType("application/json")
                .content("{\"schoolId\":" + first.getId() + "}"))
                .andExpect(status().isForbidden());
    }
}

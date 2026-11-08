package pulsoescolar_api.integration.activity;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pulsoescolar_api.dto.activity.ActivityStatus;
import pulsoescolar_api.dto.activity.CreateActivityRequest;
import pulsoescolar_api.dto.user.ReviewStudentTransferRequest;
import pulsoescolar_api.entity.activity.*;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.subject.SubjectTopic;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.service.activity.*;
import pulsoescolar_api.service.user.student.StudentEnrollmentService;
import pulsoescolar_api.service.user.student.StudentTransferService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class TeacherReportIntegrationTest {
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;
    @Autowired TeacherReportService reports;
    @Autowired ActivityService quizzes;
    @Autowired ActivityRecipientService recipients;
    @Autowired StudentEnrollmentService enrollments;
    @Autowired StudentTransferService transfers;
    School school;
    Classroom room;
    Subject subject;
    SchoolUser teacher;
    MockMvc mvc;

    @BeforeEach void setup() {
        school = school("Origem");
        teacher = person(Role.TEACHER, school);
        room = room(school);
        room.getTeachers().add(teacher);
        subject = subject(room, teacher);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        login(teacher);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void publicationRecordsRecipientsEvenWithoutSubmissionsAndEmptyAudienceHasNullPercentage() {
        subject.getTopics().add(new SubjectTopic("Tema", "Descrição", ""));
        var question = new CreateActivityRequest.Question("Pergunta", List.of("A", "B", "C", "D"), "Tema", 0);
        var input = new CreateActivityRequest(Instant.now().plusSeconds(3600), Collections.nCopies(10, question));
        quizzes.create(subject.getId(), input);
        var empty = reports.report(null, null, null).summary();
        assertThat(empty.activityCount()).isEqualTo(1);
        assertThat(empty.expectedSubmissions()).isZero();
        assertThat(empty.completionPercentage()).isNull();
        assertThat(empty.averageGrade()).isNull();
        assertThat(empty.recipientHistoryComplete()).isTrue();
        student();
        student();
        quizzes.create(subject.getId(), input);
        var summary = reports.report(null, null, null).summary();
        assertThat(summary.expectedSubmissions()).isEqualTo(2);
        assertThat(summary.submittedCount()).isZero();
        assertThat(summary.completionPercentage()).isEqualByComparingTo("0");
        assertThat(summary.studentsWithOverdue()).isZero();
    }

    @Test void includesNonRespondentsAndUsesWeightedGradesAndAssignedPairs() throws Exception {
        var students = new ArrayList<SchoolUser>();
        for (int i = 0; i < 20; i++) students.add(student());
        var first = activity(true, true);
        var second = activity(true, true);
        for (int i = 0; i < 20; i++) submit(first, students.get(i), "100");
        for (int i = 0; i < 10; i++) submit(second, students.get(i), i % 2 == 0 ? "0" : "50");
        em.flush();
        // Uma transferência posterior não muda as 40 atribuições históricas.
        students.get(19).setClassroom(null);
        var report = reports.report(school.getId(), room.getId(), ActivityStatus.FINISHED);
        var summary = report.summary();
        assertThat(summary.expectedSubmissions()).isEqualTo(40);
        assertThat(summary.submittedCount()).isEqualTo(30);
        assertThat(summary.completionPercentage()).isEqualByComparingTo("75");
        assertThat(summary.averageGrade()).isEqualByComparingTo("7.50");
        assertThat(summary.studentsWithOverdue()).isEqualTo(10);
        assertThat(summary.classroomCount()).isEqualTo(1);
        assertThat(summary.activityCount()).isEqualTo(2);
        assertThat(summary.recipientHistoryComplete()).isTrue();
        assertThat(report.classrooms().getFirst().studentCount()).isEqualTo(20);
        assertThat(report.classrooms().getFirst().schoolName()).isEqualTo("Origem");
        mvc.perform(get("/api/teacher/reports").param("schoolId", school.getId().toString())
                .param("classroomId", room.getId().toString()).param("status", "FINISHED")
                .with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.expectedSubmissions").value(40))
                .andExpect(jsonPath("$.summary.averageGrade").value(7.5));
    }

    @Test void zeroGradesCountAndUngradedOrMissingSubmissionsDoNotLowerAverage() {
        var zero = student();
        var full = student();
        var ungraded = student();
        student();
        var activity = activity(true, true);
        submit(activity, zero, "0");
        submit(activity, full, "100");
        var pending = submit(activity, ungraded, "70");
        pending.setGradedAt(null);
        pending.setScore(null);
        pending.setCorrectAnswers(null);
        var summary = reports.report(null, null, null).summary();
        assertThat(summary.expectedSubmissions()).isEqualTo(4);
        assertThat(summary.submittedCount()).isEqualTo(3);
        assertThat(summary.averageGrade()).isEqualByComparingTo("5");
        assertThat(summary.completionPercentage()).isEqualByComparingTo("75");
        assertThat(summary.studentsWithOverdue()).isEqualTo(1);
        activity(true, true);
        assertThat(reports.report(null, null, null).summary().studentsWithOverdue()).isEqualTo(4);
    }

    @Test void legacyHistoryIsUnknownAndEmptySlicesHaveNullAveragesAndPercentages() {
        var legacy = activity(true, false);
        submit(legacy, student(), "80");
        var report = reports.report(null, null, null);
        assertThat(report.summary().recipientHistoryComplete()).isFalse();
        assertThat(report.summary().expectedSubmissions()).isNull();
        assertThat(report.summary().completionPercentage()).isNull();
        assertThat(report.summary().studentsWithOverdue()).isNull();
        assertThat(report.summary().averageGrade()).isEqualByComparingTo("8");
        assertThat(report.summary().submittedCount()).isEqualTo(1);
        assertThat(report.classrooms().getFirst().studentCount()).isNull();
        var empty = reports.report(null, null, ActivityStatus.OPEN).summary();
        assertThat(empty.expectedSubmissions()).isZero();
        assertThat(empty.averageGrade()).isNull();
        assertThat(empty.completionPercentage()).isNull();
        assertThat(empty.classroomCount()).isZero();
        activity(false, true);
        assertThat(reports.report(null, null, ActivityStatus.OPEN).summary().activityCount()).isEqualTo(1);
    }

    @Test void accessIsScopedToTeacherSubjectsClassroomsAndSchools() throws Exception {
        activity(false, true);
        var unrelatedTeacher = person(Role.TEACHER, school);
        var unrelatedSubject = subject(room, unrelatedTeacher);
        var unrelated = activity(false, true);
        unrelated.setSubject(unrelatedSubject);
        var foreign = school("Outra");
        var foreignRoom = room(foreign);
        foreignRoom.getTeachers().add(teacher);
        var foreignActivity = activity(false, true);
        foreignActivity.setSubject(subject(foreignRoom, teacher));
        assertThat(reports.report(null, null, null).summary().activityCount()).isEqualTo(1);
        assertThat(reports.report(foreign.getId(), null, null).summary().activityCount()).isZero();
        assertThat(reports.report(null, foreignRoom.getId(), null).summary().activityCount()).isZero();
        subject.getCollaborators().add(unrelatedTeacher);
        room.getTeachers().add(unrelatedTeacher);
        login(unrelatedTeacher);
        assertThat(reports.report(null, null, null).summary().activityCount()).isEqualTo(2);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/teacher/reports")).andExpect(status().isUnauthorized());
        for (var role : List.of("STUDENT", "ADMIN", "PEDAGOGICAL_COORDINATOR")) {
            mvc.perform(get("/api/teacher/reports").with(user("blocked").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/teacher/reports").with(user("pending").authorities(() -> "TWO_FACTOR_PENDING")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/reports").param("status", "INVALID")
                .with(user(teacher.getEmail()).roles("TEACHER"))).andExpect(status().isBadRequest());
    }

    @Test void enrollmentAndTransferAssignOnlyOpenActivitiesAndPreserveOriginRecipients() {
        var original = student();
        var closed = activity(true, true);
        var open = activity(false, true);
        var late = person(Role.STUDENT, school);
        var originCoordinator = person(Role.PEDAGOGICAL_COORDINATOR, school);
        login(originCoordinator);
        enrollments.enroll(room.getId(), late.getId());
        assertThat(open.getRecipients()).contains(original, late);
        assertThat(closed.getRecipients()).containsExactly(original);
        enrollments.enroll(room.getId(), late.getId());
        assertThat(open.getRecipients()).hasSize(2);
        var destination = school("Destino");
        var destinationRoom = room(destination);
        var destinationSubject = subject(destinationRoom, teacher);
        var destinationOpen = activity(false, true);
        destinationOpen.setSubject(destinationSubject);
        destinationOpen.getRecipients().clear();
        var destinationClosed = activity(true, true);
        destinationClosed.setSubject(destinationSubject);
        destinationClosed.getRecipients().clear();
        var coordinator = person(Role.PEDAGOGICAL_COORDINATOR, destination);
        var transfer = new StudentTransfer();
        transfer.setStudent(original);
        transfer.setOrigin(school);
        transfer.setDestination(destination);
        transfer.setRequestedBy(originCoordinator);
        transfer.setRequestedAt(Instant.now());
        transfer.setStatus(RegistrationStatus.PENDING);
        em.persist(transfer);
        login(coordinator);
        transfers.review(transfer.getId(), new ReviewStudentTransferRequest(RegistrationStatus.APPROVED, destinationRoom.getId()));
        assertThat(original.getClassroom()).isEqualTo(destinationRoom);
        assertThat(destinationOpen.getRecipients()).containsExactly(original);
        assertThat(destinationClosed.getRecipients()).isEmpty();
        assertThat(closed.getRecipients()).containsExactly(original);
        assertThat(open.getRecipients()).contains(original, late);
    }

    private Activity activity(boolean closed, boolean complete) {
        var activity = new Activity();
        activity.setSubject(subject);
        activity.setCreatedBy(teacher);
        activity.setCreatedAt(Instant.now().minusSeconds(3600));
        activity.setClosesAt(Instant.now().plusSeconds(closed ? -60 : 3600));
        if (complete) recipients.capture(activity);
        em.persist(activity);
        return activity;
    }

    private QuizSubmission submit(Activity activity, SchoolUser student, String score) {
        var submission = new QuizSubmission();
        submission.setActivity(activity);
        submission.setStudent(student);
        submission.setSubmittedAt(Instant.now().minusSeconds(120));
        submission.setGradedAt(Instant.now());
        submission.setScore(new BigDecimal(score));
        submission.setCorrectAnswers(new BigDecimal(score).divide(BigDecimal.TEN).intValue());
        em.persist(submission);
        return submission;
    }

    private SchoolUser student() {
        var student = person(Role.STUDENT, school);
        student.setClassroom(room);
        return student;
    }

    private SchoolUser person(Role role, School school) {
        var person = new SchoolUser();
        var id = UUID.randomUUID().toString();
        person.setFullName(id); person.setRa(id); person.setEmail(id + "@test.local");
        person.setPasswordHash("unused"); person.setRole(role); person.setSchool(school);
        em.persist(person);
        return person;
    }

    private School school(String name) {
        var school = new School(); school.setNome(name);
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        em.persist(school); return school;
    }

    private Classroom room(School school) {
        var room = new Classroom(); room.setName(UUID.randomUUID().toString()); room.setIdentifier("A"); room.setSchool(school);
        em.persist(room); return room;
    }

    private Subject subject(Classroom room, SchoolUser teacher) {
        room.getTeachers().add(teacher);
        em.flush();
        var subject = new Subject(); subject.setName(UUID.randomUUID().toString()); subject.setClassroom(room); subject.setTeacher(teacher);
        em.persist(subject); return subject;
    }

    private void login(SchoolUser user) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), "unused", List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
    }
}

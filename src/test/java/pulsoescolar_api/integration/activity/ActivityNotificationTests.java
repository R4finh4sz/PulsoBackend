package pulsoescolar_api.integration.activity;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.activity.*;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.repository.activity.*;
import pulsoescolar_api.repository.classroom.ClassroomRepository;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.service.activity.ActivityNotificationService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest @Transactional
class ActivityNotificationTests {
    @Autowired ActivityNotificationService service;
    @Autowired ActivityRepository activities;
    @Autowired QuizSubmissionRepository submissions;
    @Autowired UserNotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired ClassroomRepository classrooms;
    @Autowired SubjectRepository subjects;
    @Autowired pulsoescolar_api.repository.school.SchoolRepository schoolRepository;
    @MockitoBean Clock clock;
    final Instant now = Instant.parse("2026-11-13T15:00:00Z");
    Activity activity;
    SchoolUser student;
    Classroom classroom;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        classroom = new Classroom();
        classroom.setName(UUID.randomUUID().toString()); classroom.setIdentifier("A");
        classrooms.saveAndFlush(classroom);
        var teacher = user(Role.TEACHER);
        classroom.getTeachers().add(teacher);
        classrooms.flush();
        var subject = new Subject();
        subject.setName("Matemática"); subject.setClassroom(classroom); subject.setTeacher(teacher);
        subjects.saveAndFlush(subject);
        student = user(Role.STUDENT);
        activity = new Activity();
        activity.setSubject(subject); activity.setCreatedBy(teacher); activity.setCreatedAt(now);
        activity.setClosesAt(Instant.parse("2026-11-13T21:00:00Z"));
        activity.getRecipients().add(student);
        activities.saveAndFlush(activity);
    }

    @Test void notifiesOpeningAndRemindsOnceWithLocalTime() {
        service.opened(activity);
        service.remindClosingToday();
        service.remindClosingToday();
        var result = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(student.getId());
        assertThat(result).extracting(n -> n.getType()).containsExactly("QUIZ_CLOSING_TODAY", "QUIZ_OPENED");
        assertThat(result.getFirst().getMessage()).contains("Matemática fecha hoje às 18:00!");
        assertThat(activity.isClosingReminderSent()).isTrue();
    }

    @Test void doesNotRemindCompletedInactiveOrTransferredStudents() {
        var inactive = user(Role.STUDENT); inactive.setDeletedAt(now);
        var transferred = user(Role.STUDENT); transferred.setClassroom(null);
        var wrongSchool = user(Role.STUDENT);
        wrongSchool.setSchool(newSchool());
        activity.getRecipients().addAll(java.util.List.of(inactive, transferred, wrongSchool));
        var submission = new QuizSubmission();
        submission.setActivity(activity); submission.setStudent(student); submission.setSubmittedAt(now);
        submissions.saveAndFlush(submission);
        service.remindClosingToday();
        for (var recipient : activity.getRecipients()) {
            assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId())).isEmpty();
        }
    }

    @Test void skipsClosedQuizzesAndNextLocalDay() {
        activity.setClosesAt(now);
        service.opened(activity);
        service.remindClosingToday();
        activity.setClosesAt(Instant.parse("2026-11-14T03:00:00Z"));
        service.remindClosingToday();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(student.getId())).isEmpty();
        assertThat(activity.isClosingReminderSent()).isFalse();
    }

    @Test void includesUtcTomorrowWhenItIsStillTodayInBrazil() {
        activity.setClosesAt(Instant.parse("2026-11-14T02:59:00Z"));
        service.remindClosingToday();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(student.getId()))
                .singleElement().satisfies(n -> assertThat(n.getMessage()).contains("23:59"));
    }

    @Test void skipsDeletedClassrooms() {
        classroom.setDeletedAt(now);
        service.opened(activity);
        service.remindClosingToday();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(student.getId())).isEmpty();
    }

    private SchoolUser user(Role role) {
        var value = new SchoolUser();
        var id = UUID.randomUUID().toString();
        value.setFullName(id); value.setEmail(id + "@quiz.test"); value.setRa(id);
        value.setRole(role); value.setPasswordHash("hash");
        if (role == Role.STUDENT) value.setClassroom(classroom);
        return users.saveAndFlush(value);
    }

    private pulsoescolar_api.entity.school.School newSchool() {
        var value = new pulsoescolar_api.entity.school.School();
        value.setNome("Outra"); value.setLogradouro("Rua"); value.setBairro("Centro"); value.setCidade("Recife");
        return schoolRepository.saveAndFlush(value);
    }
}

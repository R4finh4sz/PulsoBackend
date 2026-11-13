package pulsoescolar_api.service.activity;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import pulsoescolar_api.dto.activity.CreateActivityRequest;
import pulsoescolar_api.entity.activity.Activity;
import pulsoescolar_api.entity.subject.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.mapper.activity.ActivityMapper;
import pulsoescolar_api.repository.activity.*;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityYearTest {
    private final ActivityRepository activities = mock(ActivityRepository.class);

    private ActivityService service(String now) {
        var teacher = new SchoolUser(); teacher.setId(1L); teacher.setRole(Role.TEACHER);
        var subject = new Subject(); subject.setTeacher(teacher);
        subject.getTopics().add(new SubjectTopic("Tema", "Descrição", ""));
        var subjects = mock(SubjectRepository.class);
        when(subjects.findById(2L)).thenReturn(Optional.of(subject));
        var current = mock(CurrentUser.class);
        when(current.get()).thenReturn(teacher);
        return new ActivityService(activities, mock(QuizSubmissionRepository.class), subjects, current,
                mock(ClassroomAccessPolicy.class), mock(ActivityMapper.class), mock(EntityManager.class),
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC), mock(ActivityRecipientService.class),
                mock(ActivityNotificationService.class));
    }

    private CreateActivityRequest input(String deadline) {
        return new CreateActivityRequest(deadline == null ? null : Instant.parse(deadline), Collections.nCopies(10,
                new CreateActivityRequest.Question("Pergunta", List.of("A", "B", "C", "D"), "Tema", 0)));
    }

    @ParameterizedTest @CsvSource({
            "2026-11-13T12:00:00Z, 2027-01-01T02:59:59Z",
            "2027-01-01T02:00:00Z, 2027-01-01T02:59:59Z",
            "2027-01-01T03:00:00Z, 2027-01-01T04:00:00Z"
    })
    void usesCurrentBrazilianYearAndAdvancesAutomatically(String now, String deadline) {
        service(now).create(2L, input(deadline));
        var captured = ArgumentCaptor.forClass(Activity.class);
        verify(activities).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getCreatedAt()).isEqualTo(Instant.parse(now));
        assertThat(captured.getValue().getClosesAt()).isEqualTo(Instant.parse(deadline));
    }

    @ParameterizedTest @CsvSource({
            "2026-11-13T12:00:00Z, 2027-01-01T03:00:00Z",
            "2027-01-01T02:00:00Z, 2027-01-01T03:00:00Z",
            "2027-01-01T03:00:00Z, 2027-01-01T02:59:59Z",
            "2026-11-13T12:00:00Z, 2026-11-13T12:00:00Z",
            "2026-11-13T12:00:00Z,"
    })
    void refusesOtherYearsMissingOrExpiredDeadline(String now, String deadline) {
        assertThatThrownBy(() -> service(now).create(2L, input(deadline)))
                .isInstanceOf(BusinessValidationException.class);
        verifyNoInteractions(activities);
    }
}

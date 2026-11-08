package pulsoescolar_api.service.activity;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pulsoescolar_api.entity.activity.*;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.mapper.activity.ActivityMapper;
import pulsoescolar_api.repository.activity.*;
import pulsoescolar_api.repository.subject.SubjectRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.security.classroom.ClassroomAccessPolicy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferredStudentHistoryTest {
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final QuizSubmissionRepository submissions = mock(QuizSubmissionRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final ClassroomAccessPolicy access = mock(ClassroomAccessPolicy.class);
    private final ActivityMapper mapper = mock(ActivityMapper.class);
    private final ActivityService service = new ActivityService(activities, submissions,
            mock(SubjectRepository.class), current, access, mapper, mock(EntityManager.class), Clock.systemUTC(),
            mock(ActivityRecipientService.class));
    private final SchoolUser student = new SchoolUser();
    private final Activity activity = new Activity();
    private final QuizSubmission submission = new QuizSubmission();

    TransferredStudentHistoryTest() {
        student.setId(1L);
        student.setRole(Role.STUDENT);
        activity.setId(10L);
        submission.setStudent(student);
        submission.setActivity(activity);
        when(current.get()).thenReturn(student);
        when(activities.findById(10L)).thenReturn(Optional.of(activity));
        when(submissions.findById(20L)).thenReturn(Optional.of(submission));
    }

    @Test void listsCompletedQuizzesEvenWithoutAClassroom() {
        when(submissions.findByStudentIdOrderBySubmittedAtDescIdDesc(1L)).thenReturn(List.of(submission));
        when(submissions.findByActivityIdAndStudentId(10L, 1L)).thenReturn(Optional.of(submission));
        assertThat(service.studentActivities()).hasSize(1);
        verify(mapper).toResponse(activity);
        verify(mapper).toResponse(submission);
    }

    @Test void readsOwnResultAndCompletedQuizAfterTransfer() {
        service.result(20L);
        when(submissions.existsByActivityIdAndStudentId(10L, 1L)).thenReturn(true);
        service.open(10L);
        verify(mapper).toResponse(submission);
        verify(mapper).toQuiz(activity);
        verifyNoInteractions(access);
    }

    @Test void cannotReadAnotherStudentsResult() {
        var other = new SchoolUser();
        other.setId(2L);
        submission.setStudent(other);
        assertThatThrownBy(() -> service.result(20L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(mapper);
    }
}

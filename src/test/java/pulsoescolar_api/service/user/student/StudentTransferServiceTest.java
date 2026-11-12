package pulsoescolar_api.service.user.student;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pulsoescolar_api.dto.user.TransferStudentRequest;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.exception.common.*;
import pulsoescolar_api.repository.user.StudentTransferRepository;
import pulsoescolar_api.service.classroom.ClassroomLookupService;
import pulsoescolar_api.repository.school.SchoolRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentTransferServiceTest {
    private final CurrentUser current = mock(CurrentUser.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final StudentTransferRepository transfers = mock(StudentTransferRepository.class);
    private final StudentTransferService service = new StudentTransferService(current, users, schools, transfers,
            mock(ClassroomLookupService.class), mock(jakarta.persistence.EntityManager.class), java.time.Clock.systemUTC(),
            mock(pulsoescolar_api.service.activity.ActivityRecipientService.class));
    private final School origin = school(1L);
    private final School destination = school(2L);
    private final SchoolUser student = user(Role.STUDENT, origin);
    private final TransferStudentRequest request = new TransferStudentRequest("Ana", "123", 2L);

    StudentTransferServiceTest() {
        student.setFullName("Ana");
        student.setRa("123");
        student.setClassroom(new Classroom());
        when(current.get()).thenReturn(user(Role.PEDAGOGICAL_COORDINATOR, origin));
        when(users.findByRa("123")).thenReturn(Optional.of(student));
        when(schools.findById(2L)).thenReturn(Optional.of(destination));
        when(transfers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void createsPendingRequestWithoutChangingEnrollment() {
        var result = service.transfer(request);
        assertThat(result.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(result.destinationSchoolId()).isEqualTo(2L);
        assertThat(student.getSchool()).isSameAs(origin);
        assertThat(student.getSchools()).containsExactly(origin);
        assertThat(student.getClassroom()).isNotNull();
        verify(users, never()).saveAndFlush(any());
    }

    @Test void deniesStudentsAndOtherRoles() {
        for (var role : new Role[]{Role.STUDENT, Role.ADMIN, Role.TEACHER}) {
            when(current.get()).thenReturn(user(role, origin));
            assertThatThrownBy(() -> service.transfer(request)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(users, schools);
    }

    @Test void deniesCoordinatorFromAnotherSchool() {
        when(current.get()).thenReturn(user(Role.PEDAGOGICAL_COORDINATOR, destination));
        assertThatThrownBy(() -> service.transfer(request)).isInstanceOf(AccessDeniedException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void validatesNameAndDestinationBeforeChangingMembership() {
        assertThatThrownBy(() -> service.transfer(new TransferStudentRequest("Outra", "123", 2L)))
                .isInstanceOf(BusinessValidationException.class);
        assertThatThrownBy(() -> service.transfer(new TransferStudentRequest("Ana", "123", 1L)))
                .isInstanceOf(BusinessConflictException.class);
        when(schools.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(request)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(student.getSchool()).isSameAs(origin);
        assertThat(student.getClassroom()).isNotNull();
    }

    private static School school(Long id) {
        var school = new School();
        school.setId(id);
        return school;
    }

    private static SchoolUser user(Role role, School school) {
        var user = new SchoolUser();
        user.setRole(role);
        user.setSchool(school);
        return user;
    }
}

package pulsoescolar_api.service.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.security.CurrentUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentSchoolAccessTest {
    @Test void studentCannotSwitchSchoolsEvenWithALegacyMembership() {
        var origin = new School();
        origin.setId(1L);
        var other = new School();
        other.setId(2L);
        var student = new SchoolUser();
        student.setRole(Role.STUDENT);
        student.setSchool(origin);
        student.getSchools().add(other);
        student.setActiveSchool(other);
        assertThat(student.getSchool()).isSameAs(origin);
        assertThat(student.belongsToSchool(2L)).isFalse();
        var current = mock(CurrentUser.class);
        when(current.get()).thenReturn(student);
        var service = new UserProfileService(current, null, null, null, null, null);
        assertThatThrownBy(() -> service.activeSchool(2L)).isInstanceOf(AccessDeniedException.class);
    }
}

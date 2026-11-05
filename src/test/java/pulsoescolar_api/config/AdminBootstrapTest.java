package pulsoescolar_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.user.UserRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    final UserRepository users = mock(UserRepository.class);
    final PasswordEncoder passwords = mock(PasswordEncoder.class);
    final MockEnvironment environment = new MockEnvironment();

    @Test void refusesStartupWithoutAdminOrBootstrapConfiguration() {
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(users, passwords, environment).run());
    }

    @Test void refusesStartupEvenIfAdminExistsButConfigurationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(users, passwords, environment).run());
        verifyNoInteractions(users);
    }

    @Test void configuredEmailMustBelongToActiveAdmin() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@example.com");
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "admin-password-123");
        var user = new SchoolUser();
        user.setRole(Role.STUDENT);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        var bootstrap = new AdminBootstrap(users, passwords, environment);
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        user.setRole(Role.ADMIN);
        user.setDeletedAt(java.time.Instant.now());
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        user.setDeletedAt(null);
        assertDoesNotThrow(() -> bootstrap.run());
    }

    @Test void createsAdminWithMandatoryTwoFactorAndDoesNotResetExistingPassword() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", " ADMIN@example.com ");
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "admin-password-123");
        when(passwords.encode("admin-password-123")).thenReturn("hash");
        var bootstrap = new AdminBootstrap(users, passwords, environment);
        bootstrap.run();
        var saved = org.mockito.ArgumentCaptor.forClass(SchoolUser.class);
        verify(users).save(saved.capture());
        assertEquals(Role.ADMIN, saved.getValue().getRole());
        assertTrue(saved.getValue().isTwoFactorEnabled());
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(saved.getValue()));
        bootstrap.run();
        verify(users, times(1)).save(any());
        verify(passwords, times(1)).encode(any());
    }
}

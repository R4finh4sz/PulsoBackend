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

    @Test void bootstrapAndGeneratedPasswordsFollowTheSharedPolicy() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@example.com");
        var policy = new pulsoescolar_api.security.user.PasswordPolicy();
        var bootstrap = new AdminBootstrap(users, passwords, environment, policy);
        for (String weak : java.util.List.of("lowercase123", "UppercaseOnly", "Short1", "Á".repeat(36) + "A1")) {
            environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", weak);
            assertThrows(IllegalStateException.class, bootstrap::run);
        }
        verifyNoInteractions(users);
        var generator = new pulsoescolar_api.service.auth.PasswordGenerator();
        for (int i = 0; i < 10; i++) assertDoesNotThrow(() -> policy.requireValidNewPassword(generator.generate()));
    }

    @Test void refusesStartupWithoutAdminOrBootstrapConfiguration() {
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(users, passwords, environment, new pulsoescolar_api.security.user.PasswordPolicy()).run());
    }

    @Test void refusesStartupEvenIfAdminExistsButConfigurationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(users, passwords, environment, new pulsoescolar_api.security.user.PasswordPolicy()).run());
        verifyNoInteractions(users);
    }

    @Test void configuredEmailMustBelongToActiveAdmin() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@example.com");
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "Admin-password-123");
        var user = new SchoolUser();
        user.setRole(Role.STUDENT);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        var bootstrap = new AdminBootstrap(users, passwords, environment, new pulsoescolar_api.security.user.PasswordPolicy());
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        user.setRole(Role.ADMIN);
        user.setDeletedAt(java.time.Instant.now());
        assertThrows(IllegalStateException.class, () -> bootstrap.run());
        user.setDeletedAt(null);
        assertDoesNotThrow(() -> bootstrap.run());
    }

    @Test void createsAdminWithMandatoryTwoFactorAndDoesNotResetExistingPassword() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", " ADMIN@example.com ");
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "Admin-password-123");
        when(passwords.encode("Admin-password-123")).thenReturn("hash");
        var bootstrap = new AdminBootstrap(users, passwords, environment, new pulsoescolar_api.security.user.PasswordPolicy());
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

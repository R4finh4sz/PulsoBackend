package pulsoescolar_api;

import org.junit.jupiter.api.Test;
import pulsoescolar_api.security.user.PasswordPolicy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PasswordPolicyTests {
    @Test void acceptsMobileMinimumAndBcryptBoundaryIncludingUnicode() {
        var policy = new PasswordPolicy();
        assertDoesNotThrow(() -> policy.requireValidNewPassword("Nova1234"));
        assertDoesNotThrow(() -> policy.requireValidNewPassword("A1" + "a".repeat(70)));
        assertDoesNotThrow(() -> policy.requireValidNewPassword("A1" + "á".repeat(35)));
    }
}

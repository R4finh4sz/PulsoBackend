package pulsoescolar_api.security.user;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pulsoescolar_api.exception.InvalidPasswordException;
import pulsoescolar_api.exception.PasswordTooLongException;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void deveAceitarSenhaValida() {
        // Arrange
        String senha = "Nova1234";
        // Act
        // Assert
        assertDoesNotThrow(() -> policy.requireValidNewPassword(senha));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nova1234", "Novasenha", "Nova123"})
    void deveRejeitarSenhaSemRequisitosMinimos(String senha) {
        // Arrange
        String mensagem = "A senha deve ter pelo menos 8 caracteres, uma letra maiúscula e um número.";
        // Act
        var erro = assertThrows(InvalidPasswordException.class, () -> policy.requireValidNewPassword(senha));
        // Assert
        assertEquals(mensagem, erro.getMessage());
    }

    static Stream<String> senhasNoLimite() {
        return Stream.of("Nova1234", "A1" + "a".repeat(70), "A1" + "á".repeat(35));
    }

    @ParameterizedTest
    @MethodSource("senhasNoLimite")
    void deveAceitarOitoCaracteresOuSetentaEDoisBytes(String senha) {
        // Arrange
        // Act
        // Assert
        assertDoesNotThrow(() -> policy.requireValidNewPassword(senha));
    }

    static Stream<String> senhasAcimaDoLimite() {
        return Stream.of("A1" + "a".repeat(71), "A1" + "á".repeat(35) + "a");
    }

    @ParameterizedTest
    @MethodSource("senhasAcimaDoLimite")
    void deveRejeitarSetentaETresBytes(String senha) {
        // Arrange
        String mensagem = "A senha é muito longa. Use uma senha mais curta.";
        // Act
        var erro = assertThrows(PasswordTooLongException.class, () -> policy.requireValidNewPassword(senha));
        // Assert
        assertEquals(mensagem, erro.getMessage());
    }
}

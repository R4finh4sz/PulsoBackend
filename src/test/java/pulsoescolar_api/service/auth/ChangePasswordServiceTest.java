package pulsoescolar_api.service.auth;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import pulsoescolar_api.dto.auth.ChangePasswordRequest;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.exception.*;
import pulsoescolar_api.repository.auth.AuthSessionRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.user.PasswordPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuthSessionRepository sessions;
    @Mock PasswordPolicy passwordPolicy;
    @InjectMocks ChangePasswordService service;
    private final UUID jti = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("1").jti(jti.toString()).build();
    private final ChangePasswordRequest request = new ChangePasswordRequest("Atual123", "Nova1234");

    private SchoolUser usuarioExistente() {
        var user = new SchoolUser();
        user.setId(1L);
        user.setPasswordHash("hash-atual");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void deveAtualizarHashERevogarOutrasSessoesQuandoSenhaValida() {
        // Arrange
        var user = usuarioExistente();
        when(encoder.matches("Atual123", "hash-atual")).thenReturn(true);
        when(encoder.encode("Nova1234")).thenReturn("hash-novo");
        // Act
        service.change(jwt, request);
        // Assert
        assertEquals("hash-novo", user.getPasswordHash());
        verify(passwordPolicy).requireValidNewPassword("Nova1234");
        verify(sessions).revokeOthers(1L, jti);
    }

    @Test
    void deveNegarSenhaAtualIncorretaSemRevogarSessoes() {
        // Arrange
        var user = usuarioExistente();
        // Act
        var erro = assertThrows(BusinessValidationException.class, () -> service.change(jwt, request));
        // Assert
        assertEquals("Senha atual incorreta.", erro.getMessage());
        assertEquals("hash-atual", user.getPasswordHash());
        verify(sessions, never()).revokeOthers(any(), any());
        verifyNoInteractions(passwordPolicy);
    }

    @Test
    void deveNegarUsuarioInexistente() {
        // Arrange
        when(users.findById(1L)).thenReturn(Optional.empty());
        // Act
        var erro = assertThrows(SessionUnavailableException.class, () -> service.change(jwt, request));
        // Assert
        assertEquals("Sessão inválida ou expirada.", erro.getMessage());
        verifyNoInteractions(encoder, passwordPolicy, sessions);
    }

    @Test
    void deveNegarNovaSenhaIgualAtualSemCodificar() {
        // Arrange
        var user = usuarioExistente();
        var iguais = new ChangePasswordRequest("Atual123", "Atual123");
        when(encoder.matches("Atual123", "hash-atual")).thenReturn(true);
        // Act
        var erro = assertThrows(BusinessValidationException.class, () -> service.change(jwt, iguais));
        // Assert
        assertEquals("A nova senha deve ser diferente da atual.", erro.getMessage());
        assertEquals("hash-atual", user.getPasswordHash());
        verify(encoder, never()).encode(any());
        verifyNoInteractions(sessions);
    }

    @Test
    void devePreservarHashESessoesQuandoPoliticaRejeitarNovaSenha() {
        // Arrange
        var user = usuarioExistente();
        when(encoder.matches("Atual123", "hash-atual")).thenReturn(true);
        doThrow(new InvalidPasswordException()).when(passwordPolicy).requireValidNewPassword("Nova1234");
        // Act
        var erro = assertThrows(InvalidPasswordException.class, () -> service.change(jwt, request));
        // Assert
        assertEquals("A senha deve ter pelo menos 8 caracteres, uma letra maiúscula e um número.", erro.getMessage());
        assertEquals("hash-atual", user.getPasswordHash());
        verify(encoder, never()).encode(any());
        verifyNoInteractions(sessions);
    }
}

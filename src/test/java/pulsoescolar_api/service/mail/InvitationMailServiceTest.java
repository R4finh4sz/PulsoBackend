package pulsoescolar_api.service.mail;

import org.junit.jupiter.api.Test;
import pulsoescolar_api.exception.mail.EmailDeliveryException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvitationMailServiceTest {
    private final EmailSender sender = mock(EmailSender.class);

    @Test void invitationOpensFrontendPageAndExplainsExpiry() {
        var service = new InvitationMailService(sender, "https://pulsoescolar.com.br/invite");
        service.sendInvitation("teacher@example.com", "test-token");
        var text = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("teacher@example.com"), eq("Convite para o Pulso Escolar"), text.capture());
        assertTrue(text.getValue().contains("https://pulsoescolar.com.br/invite/test-token"));
        assertTrue(text.getValue().contains("48 horas"));
        assertFalse(text.getValue().contains("/api/invite"));
        assertFalse(text.getValue().contains("Senha:"));
    }

    @Test void deliveryFailurePropagatesToInvitationTransaction() {
        doThrow(new EmailDeliveryException()).when(sender).send(anyString(), anyString(), anyString());
        var service = new InvitationMailService(sender, "https://pulsoescolar.com.br/invite");
        assertThrows(EmailDeliveryException.class, () -> service.sendInvitation("teacher@example.com", "test-token"));
        assertThrows(EmailDeliveryException.class, () -> service.sendCode("teacher@example.com", "123456"));
    }
}

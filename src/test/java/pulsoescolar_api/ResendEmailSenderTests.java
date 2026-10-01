package pulsoescolar_api;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pulsoescolar_api.config.ResendProperties;
import pulsoescolar_api.exception.EmailDeliveryException;
import pulsoescolar_api.service.mail.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ResendEmailSenderTests {
    private final Emails emails = mock(Emails.class);
    private final ResendProperties properties = new ResendProperties("re_test_secret", "Pulso <no-reply@example.com>");
    private final ResendEmailSender sender = new ResendEmailSender(emails, properties);

    @Test void mapsAllMessageTypesToResendWithoutChangingTheirContent() throws Exception {
        new TwoFactorMailService(sender).send("person@example.com", "123456");
        var invitations = new InvitationMailService(sender, "https://app.example.com/invitations", "");
        invitations.sendInvitation("person@example.com", "invitation-token");
        invitations.sendCode("person@example.com", "654321");
        new WelcomeMailService(sender).send("person@example.com", "Pessoa Teste", "initial-password");
        var captor = org.mockito.ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails, times(4)).send(captor.capture());
        var messages = captor.getAllValues();
        for (var message : messages) {
            assertEquals("Pulso <no-reply@example.com>", message.getFrom());
            assertEquals(java.util.List.of("person@example.com"), message.getTo());
            assertNull(message.getHtml());
        }
        assertEquals("Código de autenticação - Pulso Escolar", messages.get(0).getSubject());
        assertTrue(messages.get(0).getText().contains("123456"));
        assertTrue(messages.get(1).getText().contains("https://app.example.com/invitations/invitation-token"));
        assertTrue(messages.get(2).getText().contains("654321"));
        assertTrue(messages.get(3).getText().contains("Pessoa Teste"));
        assertTrue(messages.get(3).getText().contains("initial-password"));
    }

    @Test void missingConfigurationDoesNotContactProvider() {
        for (var config : java.util.List.of(new ResendProperties("", "no-reply@example.com"),
                new ResendProperties("re_test", " "), new ResendProperties(null, null))) {
            assertThrows(EmailDeliveryException.class,
                    () -> new ResendEmailSender(emails, config).send("person@example.com", "Assunto", "Texto"));
        }
        verifyNoInteractions(emails);
        assertFalse(properties.toString().contains("re_test_secret"));
    }

    @Test void providerErrorsAreSanitizedAndNotRetried(CapturedOutput output) throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(429, "{\"message\":\"re_test_secret body-secret\"}"));
        var error = assertThrows(EmailDeliveryException.class,
                () -> sender.send("person@example.com", "Assunto", "body-secret"));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(output.getAll().contains("re_test_secret"));
        assertFalse(output.getAll().contains("body-secret"));
        verify(emails, times(1)).send(any(CreateEmailOptions.class));
    }

    @Test void unexpectedSdkFailureIsSanitized() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new IllegalStateException("private response"));
        var error = assertThrows(EmailDeliveryException.class,
                () -> sender.send("person@example.com", "Assunto", "Texto"));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("private response"));
    }
}

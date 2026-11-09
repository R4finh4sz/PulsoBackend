package pulsoescolar_api.service.terms;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.terms.TermsRepository;
import pulsoescolar_api.repository.terms.TermsVersionRepository;
import pulsoescolar_api.repository.user.UserRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.service.audit.AuditService;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TermsServiceTest {
    @Test
    void adminCannotRevokeEvenWhenServiceIsCalledDirectly() {
        var currentUser = mock(CurrentUser.class);
        var admin = new pulsoescolar_api.entity.user.SchoolUser();
        admin.setRole(pulsoescolar_api.entity.user.Role.ADMIN);
        when(currentUser.get()).thenReturn(admin);
        var sessions = mock(pulsoescolar_api.repository.auth.AuthSessionRepository.class);
        var revocations = mock(pulsoescolar_api.repository.terms.TermsRevocationRepository.class);
        var entityManager = mock(jakarta.persistence.EntityManager.class);
        var service = new TermsService(mock(AuditService.class), mock(TermsRepository.class),
                mock(TermsVersionRepository.class), mock(UserRepository.class), currentUser,
                sessions, revocations, entityManager, java.time.Clock.systemUTC());

        assertThatThrownBy(service::revoke).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(sessions, revocations, entityManager);
    }

    @Test
    void missingLockedTermsProducesNotFoundDuringPublishAndAcceptance() {
        var terms = mock(TermsRepository.class);
        var currentUser = mock(CurrentUser.class);
        var admin = new pulsoescolar_api.entity.user.SchoolUser();
        admin.setRole(pulsoescolar_api.entity.user.Role.ADMIN);
        when(currentUser.get()).thenReturn(admin);
        var service = new TermsService(mock(AuditService.class), terms, mock(TermsVersionRepository.class),
                mock(UserRepository.class), currentUser, mock(pulsoescolar_api.repository.auth.AuthSessionRepository.class),
                mock(pulsoescolar_api.repository.terms.TermsRevocationRepository.class),
                mock(jakarta.persistence.EntityManager.class), java.time.Clock.systemUTC());
        var request = new pulsoescolar_api.dto.terms.TermsRequest("Título", "Conteúdo");

        assertThatThrownBy(() -> service.publish(request, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.publish(request, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.accept(new pulsoescolar_api.dto.terms.AcceptTermsRequest("1.0", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingTermsProducesNotFoundInsteadOfNoSuchElement() {
        var terms = mock(TermsRepository.class);
        when(terms.findById(1L)).thenReturn(Optional.empty());
        var service = new TermsService(mock(AuditService.class), terms, mock(TermsVersionRepository.class),
                mock(UserRepository.class), mock(CurrentUser.class), mock(pulsoescolar_api.repository.auth.AuthSessionRepository.class),
                mock(pulsoescolar_api.repository.terms.TermsRevocationRepository.class),
                mock(jakarta.persistence.EntityManager.class), java.time.Clock.systemUTC());

        assertThatThrownBy(service::get).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Nenhum termo publicado.");
    }
}

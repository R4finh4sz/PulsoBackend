package pulsoescolar_api.service.user;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pulsoescolar_api.dto.user.UserExportResponse.AcceptedTerm;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.terms.TermsVersionRepository;
import pulsoescolar_api.security.CurrentUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserExportServiceTest {
    @Test void exportsOnlyAcceptedTermTitlesAndVersionsInVersionOrder() {
        var current = mock(CurrentUser.class);
        var terms = mock(TermsVersionRepository.class);
        var user = new SchoolUser();
        user.setAcceptedTermVersions(Set.of(1L, 3L));
        when(current.get()).thenReturn(user);
        when(terms.findAllById(Set.of(1L, 3L))).thenReturn(List.of(
                new TermsVersion(3L, "Terceiro", "Conteúdo terceiro"),
                new TermsVersion(1L, "Primeiro", "Conteúdo primeiro")));
        var service = new UserExportService(current, Clock.systemUTC(), terms);
        var response = service.export();
        assertThat(response.termsAcceptedVersions()).containsExactly("1.0", "1.2");
        assertThat(response.acceptedTerms()).containsExactly(
                new AcceptedTerm("Primeiro", "1.0"),
                new AcceptedTerm("Terceiro", "1.2"));
        assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(response.acceptedTerms()))
                .doesNotContain("content", "Conteúdo");
        verify(terms).findAllById(Set.of(1L, 3L));
    }
}

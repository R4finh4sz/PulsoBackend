package pulsoescolar_api.service.user;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.repository.terms.TermsVersionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserExportService {
    private final CurrentUser currentUser;
    private final Clock clock;
    private final TermsVersionRepository termsVersions;

    public UserExportResponse export() {
        var user = currentUser.get();
        return new UserExportResponse(clock.instant(), new UserExportResponse.PersonalData(
                user.getId(), user.getFullName(), user.getRa(), user.getEmail(), user.getRole()),
                user.getBirthDate(), user.getProfilePhoto(), user.isTermsAccepted(), user.getTermsAcceptedVersions(),
                termsVersions.findAllById(user.getAcceptedTermVersions()).stream()
                        .sorted(java.util.Comparator.comparing(TermsVersion::getVersion))
                        .map(term -> new UserExportResponse.AcceptedTerm(term.getTitle(), TermsVersion.label(term.getVersion())))
                        .toList());
    }
}

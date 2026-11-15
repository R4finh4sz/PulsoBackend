package pulsoescolar_api.service.user;

import java.time.Clock;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.entity.terms.TermsVersion;
import pulsoescolar_api.repository.legal.LegalDocumentRepository;
import pulsoescolar_api.repository.terms.TermsVersionRepository;
import pulsoescolar_api.service.legal.LegalDocumentService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserExportService {
    private final CurrentUser currentUser;
    private final Clock clock;
    private final TermsVersionRepository termsVersions;
    private final LegalDocumentRepository legalDocuments;

    public UserExportResponse export() {
        var user = currentUser.get();
        var terms = termsVersions.findAllById(user.getAcceptedTermVersions()).stream()
                .sorted(java.util.Comparator.comparing(TermsVersion::getVersion))
                .map(term -> new UserExportResponse.AcceptedTerm(term.getTitle(), TermsVersion.label(term.getVersion())));
        var policies = legalDocuments.findByTypeOrderByVersionAsc(LegalDocumentType.PRIVACY_POLICY).stream()
                .filter(policy -> user.getAcceptedPrivacyVersions().contains(policy.getVersion()))
                .map(policy -> new UserExportResponse.AcceptedTerm(policy.getTitle(), LegalDocumentService.label(policy.getVersion())));
        var studentTerms = legalDocuments.findByTypeOrderByVersionAsc(LegalDocumentType.STUDENT_TERM).stream()
                .filter(document -> user.getAcceptedStudentTermVersions().contains(document.getVersion()))
                .map(document -> new UserExportResponse.AcceptedTerm(document.getTitle(), LegalDocumentService.label(document.getVersion())));
        return new UserExportResponse(clock.instant(), new UserExportResponse.PersonalData(
                user.getId(), user.getFullName(), user.getRa(), user.getEmail(), user.getRole()),
                user.getBirthDate(), user.getProfilePhoto(), user.isTermsAccepted(), user.getTermsAcceptedVersions(),
                Stream.concat(Stream.concat(terms, studentTerms), policies).toList());
    }
}

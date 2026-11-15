package pulsoescolar_api.service.legal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;

/**
 * Validates acceptance of the policy alongside the document applicable to each role.
 * The optional resolver is kept for administrator flows; school roles use resolveRequired.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivacyPolicyAcceptanceService {
    private final LegalDocumentService legalDocuments;

    /**
     * Validates what the user sent and returns the accepted version, or null when no policy is published.
     * Requires an explicit acceptance of exactly the current version.
     */
    public Integer resolve(Boolean accepted, String versionLabel) {
        var policy = legalDocuments.current(LegalDocumentType.PRIVACY_POLICY).orElse(null);
        if (policy == null) return null;
        return validate(policy, accepted, versionLabel);
    }

    public Integer resolveRequired(Boolean accepted, String versionLabel) {
        return validate(legalDocuments.lockCurrentForAcceptance(LegalDocumentType.PRIVACY_POLICY), accepted, versionLabel);
    }

    private int validate(pulsoescolar_api.entity.legal.LegalDocument policy, Boolean accepted, String versionLabel) {
        if (!Boolean.TRUE.equals(accepted)) {
            throw new BusinessValidationException("É necessário aceitar a política de privacidade.");
        }
        if (!LegalDocumentService.label(policy.getVersion()).equals(versionLabel)) {
            throw new BusinessConflictException(
                    "A política de privacidade foi atualizada. Leia a nova versão antes de aceitar.");
        }
        return policy.getVersion();
    }

    /** True when no policy is published or the given accepted version is the current one. */
    public boolean satisfiedBy(Integer acceptedVersion) {
        var policy = legalDocuments.current(LegalDocumentType.PRIVACY_POLICY).orElse(null);
        return policy == null || acceptedVersion != null && acceptedVersion == policy.getVersion();
    }
}

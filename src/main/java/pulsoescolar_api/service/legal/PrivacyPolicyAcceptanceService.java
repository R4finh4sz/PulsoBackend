package pulsoescolar_api.service.legal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivacyPolicyAcceptanceService {
    private final LegalDocumentService legalDocuments;

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
    public boolean satisfiedBy(Integer acceptedVersion) {
        var policy = legalDocuments.current(LegalDocumentType.PRIVACY_POLICY).orElse(null);
        return policy == null || acceptedVersion != null && acceptedVersion == policy.getVersion();
    }
}

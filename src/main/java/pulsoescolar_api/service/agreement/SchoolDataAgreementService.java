package pulsoescolar_api.service.agreement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.agreement.DataAgreementDtos.*;
import pulsoescolar_api.entity.agreement.SchoolDataAgreement;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.repository.agreement.SchoolDataAgreementRepository;
import pulsoescolar_api.security.CurrentUser;
import pulsoescolar_api.service.legal.LegalDocumentService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolDataAgreementService {
    private final SchoolDataAgreementRepository agreements;
    private final LegalDocumentService legalDocuments;
    private final CurrentUser currentUser;
    private final java.time.Clock clock;

    public Response current() {
        var document = legalDocuments.requireCurrent(LegalDocumentType.SCHOOL_DATA_AGREEMENT);
        var school = currentUser.get().getSchool();
        var accepted = school == null ? null
                : agreements.findBySchoolIdAndVersion(school.getId(), document.getVersion()).orElse(null);
        return new Response(LegalDocumentService.label(document.getVersion()), document.getTitle(),
                document.getContent(), accepted != null, accepted == null ? null : accepted.getAcceptedAt());
    }

    @Transactional
    public Response accept(AcceptRequest request, String ipAddress) {
        var document = legalDocuments.requireCurrent(LegalDocumentType.SCHOOL_DATA_AGREEMENT);
        if (!LegalDocumentService.label(document.getVersion()).equals(request.version())) {
            throw new BusinessConflictException("O termo de adesão institucional e tratamento de dados foi atualizado. Leia a nova versão antes de aceitar.");
        }
        var user = currentUser.get();
        var school = user.getSchool();
        if (school == null) throw new BusinessValidationException("Usuário sem escola ativa.");
        if (!agreements.existsBySchoolIdAndVersion(school.getId(), document.getVersion())) {
            agreements.save(new SchoolDataAgreement(school, user, document.getVersion(),
                    document.getContentHash(), clock.instant(), ipAddress));
        }
        return current();
    }

    public void requireAccepted(Long schoolId) {
        var document = legalDocuments.current(LegalDocumentType.SCHOOL_DATA_AGREEMENT).orElseThrow(
                () -> new BusinessConflictException(
                        "Nenhum termo de adesão institucional e tratamento de dados foi publicado. Contate o administrador do Pulso."));
        if (!agreements.existsBySchoolIdAndVersion(schoolId, document.getVersion())) {
            throw new BusinessConflictException(
                    "O coordenador precisa aceitar o termo de adesão institucional e tratamento de dados da escola antes de aprovar cadastros.");
        }
    }
}

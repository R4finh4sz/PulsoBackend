package pulsoescolar_api.integration.support;

import java.time.Instant;
import pulsoescolar_api.entity.agreement.SchoolDataAgreement;
import pulsoescolar_api.entity.legal.LegalDocument;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.agreement.SchoolDataAgreementRepository;
import pulsoescolar_api.repository.legal.LegalDocumentRepository;

public final class AgreementFixture {
    private AgreementFixture() {}

    public static LegalDocument publish(LegalDocumentRepository documents, LegalDocumentType type, SchoolUser by) {
        return documents.findFirstByTypeOrderByVersionDesc(type).orElseGet(() -> documents.saveAndFlush(
                new LegalDocument(type, 1, "Documento de teste", "Conteúdo de teste", "hash-de-teste", Instant.now(), by)));
    }

    public static void accept(LegalDocumentRepository documents, SchoolDataAgreementRepository agreements,
                              School school, SchoolUser by) {
        var document = publish(documents, LegalDocumentType.SCHOOL_DATA_AGREEMENT, by);
        agreements.saveAndFlush(new SchoolDataAgreement(school, by, document.getVersion(), document.getContentHash(),
                Instant.now(), "127.0.0.1"));
    }
}

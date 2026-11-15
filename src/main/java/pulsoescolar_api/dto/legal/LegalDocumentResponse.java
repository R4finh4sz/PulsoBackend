package pulsoescolar_api.dto.legal;

import java.time.Instant;
import pulsoescolar_api.entity.legal.LegalDocument;
import pulsoescolar_api.service.legal.LegalDocumentService;

public record LegalDocumentResponse(String title, String version, String content, Instant publishedAt) {
    public static LegalDocumentResponse from(LegalDocument document) {
        return new LegalDocumentResponse(document.getTitle(), LegalDocumentService.label(document.getVersion()),
                document.getContent(), document.getPublishedAt());
    }
}

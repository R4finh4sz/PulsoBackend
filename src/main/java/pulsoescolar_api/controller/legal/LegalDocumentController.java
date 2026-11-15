package pulsoescolar_api.controller.legal;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.legal.LegalDocumentResponse;
import pulsoescolar_api.dto.terms.TermsRequest;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.service.legal.LegalDocumentService;

/**
 * One controller for the three versioned legal documents:
 * /api/privacy-policy, /api/guardian-term, /api/student-term and /api/data-agreement.
 * Reading rules (public or admin only) live in SecurityConfig; publishing is admin only.
 */
@RestController
@RequestMapping("/api/{slug:privacy-policy|guardian-term|data-agreement|student-term}")
@RequiredArgsConstructor
public class LegalDocumentController {
    private final LegalDocumentService service;

    @GetMapping
    public LegalDocumentResponse current(@PathVariable String slug) { return service.get(type(slug)); }

    @GetMapping("/history")
    public List<LegalDocumentResponse> history(@PathVariable String slug) { return service.history(type(slug)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegalDocumentResponse create(@PathVariable String slug, @Valid @RequestBody TermsRequest request) {
        return service.publish(type(slug), request, true);
    }

    @PutMapping
    public LegalDocumentResponse update(@PathVariable String slug, @Valid @RequestBody TermsRequest request) {
        return service.publish(type(slug), request, false);
    }

    private static LegalDocumentType type(String slug) {
        return LegalDocumentType.fromSlug(slug).orElseThrow(() -> new ResourceNotFoundException("Documento não encontrado."));
    }
}

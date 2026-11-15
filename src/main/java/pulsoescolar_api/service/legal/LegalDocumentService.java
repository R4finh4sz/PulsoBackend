package pulsoescolar_api.service.legal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.legal.LegalDocumentResponse;
import pulsoescolar_api.dto.terms.TermsRequest;
import pulsoescolar_api.entity.legal.LegalDocument;
import pulsoescolar_api.entity.legal.LegalDocumentType;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.exception.common.BusinessConflictException;
import pulsoescolar_api.exception.common.ResourceNotFoundException;
import pulsoescolar_api.repository.legal.LegalDocumentRepository;
import pulsoescolar_api.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalDocumentService {
    private final LegalDocumentRepository documents;
    private final pulsoescolar_api.repository.user.UserRepository users;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final pulsoescolar_api.repository.terms.TermsRepository terms;

    public static String label(int version) { return "1." + (version - 1); }

    public Optional<LegalDocument> current(LegalDocumentType type) {
        return documents.findFirstByTypeOrderByVersionDesc(type);
    }

    public LegalDocument requireCurrent(LegalDocumentType type) {
        return current(type).orElseThrow(
                () -> new ResourceNotFoundException("Nenhum documento publicado: " + type.displayName() + "."));
    }

    public LegalDocument lockCurrentForAcceptance(LegalDocumentType type) {
        terms.lockCurrent();
        return requireCurrent(type);
    }

    public LegalDocumentResponse get(LegalDocumentType type) {
        return LegalDocumentResponse.from(requireCurrent(type));
    }

    public List<LegalDocumentResponse> history(LegalDocumentType type) {
        return documents.findByTypeOrderByVersionAsc(type).stream().map(LegalDocumentResponse::from).toList();
    }

    @Transactional
    public LegalDocumentResponse publish(LegalDocumentType type, TermsRequest request, boolean create) {
        var actor = currentUser.get();
        if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Esta operação exige acesso de administrador.");
        }
        terms.lockCurrent();
        var latest = current(type);
        if (create && latest.isPresent()) {
            throw new BusinessConflictException("Já existe " + type.displayName() + " publicado(a). Use a rota de edição.");
        }
        if (!create && latest.isEmpty()) {
            throw new ResourceNotFoundException("Nenhum documento publicado: " + type.displayName() + ".");
        }
        var content = request.content();
        var document = new LegalDocument(type, latest.map(value -> value.getVersion() + 1).orElse(1),
                request.title().strip(), content, sha256(content), clock.instant(), actor);
        var saved = documents.saveAndFlush(document);
        if (type == LegalDocumentType.PRIVACY_POLICY) users.resetTermsAcceptance();
        if (type == LegalDocumentType.STUDENT_TERM) users.resetStudentTermAcceptance(
                java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("America/Sao_Paulo")).minusYears(18));
        return LegalDocumentResponse.from(saved);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

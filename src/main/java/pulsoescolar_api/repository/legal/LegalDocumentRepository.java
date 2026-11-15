package pulsoescolar_api.repository.legal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.legal.LegalDocument;
import pulsoescolar_api.entity.legal.LegalDocumentType;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {
    Optional<LegalDocument> findFirstByTypeOrderByVersionDesc(LegalDocumentType type);
    List<LegalDocument> findByTypeOrderByVersionAsc(LegalDocumentType type);
}

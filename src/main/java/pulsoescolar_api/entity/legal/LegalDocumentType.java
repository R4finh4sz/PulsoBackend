package pulsoescolar_api.entity.legal;

import java.util.Arrays;
import java.util.Optional;

public enum LegalDocumentType {
    PRIVACY_POLICY("privacy-policy", "política de privacidade"),
    GUARDIAN_TERM("guardian-term", "termo do responsável legal"),
    SCHOOL_DATA_AGREEMENT("data-agreement", "termo de adesão institucional e tratamento de dados"),
    STUDENT_TERM("student-term", "termo do estudante");

    private final String slug;
    private final String displayName;

    LegalDocumentType(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public String slug() { return slug; }
    public String displayName() { return displayName; }

    public static Optional<LegalDocumentType> fromSlug(String slug) {
        return Arrays.stream(values()).filter(type -> type.slug.equals(slug)).findFirst();
    }
}

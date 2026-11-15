package pulsoescolar_api.entity.legal;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import pulsoescolar_api.entity.user.SchoolUser;

/** One published version of a legal document. Versions are never edited; a change publishes a new one. */
@Entity
@Table(name = "legal_documents")
@Immutable
@Getter
@NoArgsConstructor
public class LegalDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private LegalDocumentType type;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 64) private String contentHash;
    @Column(nullable = false) private Instant publishedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_by_id") private SchoolUser publishedBy;

    public LegalDocument(LegalDocumentType type, int version, String title, String content, String contentHash,
                         Instant publishedAt, SchoolUser publishedBy) {
        this.type = type;
        this.version = version;
        this.title = title;
        this.content = content;
        this.contentHash = contentHash;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
    }
}

package pulsoescolar_api.repository.terms;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.terms.TermsVersion;
public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {
    List<TermsVersion> findAllByOrderByVersionAsc();
}

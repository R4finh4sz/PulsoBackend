package pulsoescolar_api.repository.terms;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.terms.TermsOfUse;

public interface TermsRepository extends JpaRepository<TermsOfUse, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TermsOfUse t WHERE t.id = 1")
    TermsOfUse lockCurrent();
}

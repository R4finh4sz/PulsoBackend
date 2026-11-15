package pulsoescolar_api.repository.agreement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.agreement.SchoolDataAgreement;

public interface SchoolDataAgreementRepository extends JpaRepository<SchoolDataAgreement, Long> {
    Optional<SchoolDataAgreement> findBySchoolIdAndVersion(Long schoolId, int version);
    boolean existsBySchoolIdAndVersion(Long schoolId, int version);
}

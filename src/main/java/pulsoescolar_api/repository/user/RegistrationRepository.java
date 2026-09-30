package pulsoescolar_api.repository.user;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.user.RegistrationRequest;

public interface RegistrationRepository extends JpaRepository<RegistrationRequest, Long>,
        JpaSpecificationExecutor<RegistrationRequest> {
    boolean existsByEmailOrRa(String email, String ra);
    Optional<RegistrationRequest> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RegistrationRequest r WHERE r.id = :id")
    Optional<RegistrationRequest> findForReview(Long id);
}

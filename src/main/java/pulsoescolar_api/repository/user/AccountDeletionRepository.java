package pulsoescolar_api.repository.user;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.user.*;

public interface AccountDeletionRepository extends JpaRepository<AccountDeletionRequest, Long>,
        JpaSpecificationExecutor<AccountDeletionRequest> {
    boolean existsByRequesterIdAndStatus(Long studentId, DeletionStatus status);
    List<AccountDeletionRequest> findByRequesterIdOrderByRequestedAtDesc(Long studentId);
}

package pulsoescolar_api.repository.user;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.user.*;

public interface AccountDeletionRepository extends JpaRepository<AccountDeletionRequest, Long>,
        JpaSpecificationExecutor<AccountDeletionRequest> {
    boolean existsByRequesterIdAndStatus(Long studentId, DeletionStatus status);
    List<AccountDeletionRequest> findByRequesterIdOrderByRequestedAtDesc(Long studentId);
    java.util.Optional<AccountDeletionRequest> findFirstByRequesterIdOrderByRequestedAtDescIdDesc(Long requesterId);

    @Query("select r.id from AccountDeletionRequest r where r.status = 'PENDING' and r.requestedAt <= :cutoff "
            + "and r.requester.deletedAt is null and r.requester.role <> 'ADMIN' order by r.requestedAt, r.id")
    List<Long> findDueIds(java.time.Instant cutoff);
}

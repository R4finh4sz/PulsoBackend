package pulsoescolar_api.repository.user;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import pulsoescolar_api.entity.user.*;

public interface StudentTransferRepository extends JpaRepository<StudentTransfer, Long> {
    boolean existsByStudentIdAndStatus(Long studentId, RegistrationStatus status);
    List<StudentTransfer> findByDestinationIdAndStatusOrderByRequestedAtAscIdAsc(Long destinationId, RegistrationStatus status);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StudentTransfer t where t.id = :id")
    Optional<StudentTransfer> findForReview(Long id);
}

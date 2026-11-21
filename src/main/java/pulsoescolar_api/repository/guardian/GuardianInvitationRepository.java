package pulsoescolar_api.repository.guardian;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pulsoescolar_api.entity.guardian.GuardianInvitation;

public interface GuardianInvitationRepository extends JpaRepository<GuardianInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GuardianInvitation i join fetch i.confirmation where i.tokenHash = :hash")
    Optional<GuardianInvitation> findForDecision(@Param("hash") String hash);

    @Query("select i from GuardianInvitation i join fetch i.confirmation where i.tokenHash = :hash")
    Optional<GuardianInvitation> findForView(@Param("hash") String hash);

    Optional<GuardianInvitation> findFirstByConfirmationIdOrderByIdDesc(Long confirmationId);

    Optional<GuardianInvitation> findFirstByConfirmationIdAndDecidedAtIsNotNullOrderByIdDesc(Long confirmationId);

    Optional<GuardianInvitation> findFirstByConfirmationIdAndRenewalSessionIdIsNotNullOrderByIdDesc(Long confirmationId);

    int countByConfirmationIdAndRenewalSessionId(Long confirmationId, java.util.UUID sessionId);

    void deleteByConfirmationId(Long confirmationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update GuardianInvitation i set i.supersededAt = :now
            where i.confirmation.id = :confirmationId and i.decidedAt is null and i.supersededAt is null
            """)
    int supersedeOpen(@Param("confirmationId") Long confirmationId, @Param("now") Instant now);
}

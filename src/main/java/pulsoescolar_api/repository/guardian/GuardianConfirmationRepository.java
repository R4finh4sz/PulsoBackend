package pulsoescolar_api.repository.guardian;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pulsoescolar_api.entity.guardian.*;

public interface GuardianConfirmationRepository extends JpaRepository<GuardianConfirmation, Long> {
    Optional<GuardianConfirmation> findByRegistrationId(Long registrationId);

    Optional<GuardianConfirmation> findByRegistrationUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GuardianConfirmation c where c.registration.user.id = :userId")
    Optional<GuardianConfirmation> lockByRegistrationUserId(@Param("userId") Long userId);

    Page<GuardianConfirmation> findByStatusOrderByCreatedAtAscIdAsc(GuardianStatus status, Pageable pageable);

    Page<GuardianConfirmation> findByStatusAndRegistrationSchoolIdOrderByCreatedAtAscIdAsc(
            GuardianStatus status, Long schoolId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GuardianConfirmation c where c.id = :id")
    Optional<GuardianConfirmation> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GuardianConfirmation c where c.correctionTokenHash = :hash")
    Optional<GuardianConfirmation> findForCorrection(@Param("hash") String hash);

    /** An expired invitation is neither a refusal nor a confirmation: the process just becomes EXPIRED (RN18). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GuardianConfirmation c set c.status = pulsoescolar_api.entity.guardian.GuardianStatus.EXPIRED,
                   c.updatedAt = :now, c.version = c.version + 1
            where c.status = pulsoescolar_api.entity.guardian.GuardianStatus.AWAITING_GUARDIAN
              and not exists (select 1 from GuardianInvitation i where i.confirmation = c
                  and i.decidedAt is null and i.supersededAt is null and i.expiresAt > :now)
            """)
    int expireOverdue(@Param("now") Instant now);
}

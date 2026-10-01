package pulsoescolar_api.repository.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import pulsoescolar_api.entity.user.UserInvitation;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
    boolean existsByEmailAndUsedAtIsNullAndExpiresAtAfter(String email, java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvitation i where i.tokenHash = :tokenHash")
    Optional<UserInvitation> findForUpdate(String tokenHash);
}

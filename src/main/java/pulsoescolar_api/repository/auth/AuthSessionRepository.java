package pulsoescolar_api.repository.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.auth.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s WHERE s.id = :id")
    java.util.Optional<AuthSession> lockById(@Param("id") UUID id);

    @Query("SELECT COUNT(s) > 0 FROM AuthSession s WHERE s.id = :id AND s.user.id = :userId AND s.expiresAt > :now")
    boolean isActive(@Param("id") UUID id, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuthSession s WHERE s.id = :id")
    void revoke(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuthSession s WHERE s.user.id = :userId AND s.id <> :currentSessionId")
    void revokeOthers(@Param("userId") Long userId, @Param("currentSessionId") UUID currentSessionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}

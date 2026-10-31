package pulsoescolar_api.repository.notification;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import pulsoescolar_api.entity.notification.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from UserNotification n where n.id = :id and n.recipient.id = :recipientId")
    Optional<UserNotification> findMineForUpdate(Long id, Long recipientId);
}

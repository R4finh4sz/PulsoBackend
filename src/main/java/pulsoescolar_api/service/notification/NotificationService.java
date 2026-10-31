package pulsoescolar_api.service.notification;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.dto.notification.NotificationResponse;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.notification.UserNotificationRepository;
import pulsoescolar_api.security.CurrentUser;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final UserNotificationRepository notifications;
    private final CurrentUser currentUser;
    private final Clock clock;

    public List<NotificationResponse> mine() {
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(currentUser.get().getId()).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional
    public void read(Long id) {
        var notification = notifications.findMineForUpdate(id, currentUser.get().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada."));
        if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
    }
}

package pulsoescolar_api.dto.notification;

import java.time.Instant;
import pulsoescolar_api.entity.notification.UserNotification;

public record NotificationResponse(Long id, String type, String title, String message, boolean read,
                                   Instant readAt, Instant createdAt, String readUrl) {
    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getReadAt() != null, notification.getReadAt(),
                notification.getCreatedAt(), "/api/notifications/" + notification.getId() + "/read");
    }
}

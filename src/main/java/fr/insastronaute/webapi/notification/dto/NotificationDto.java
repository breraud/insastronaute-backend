package fr.insastronaute.webapi.notification.dto;

import fr.insastronaute.webapi.notification.domain.Notification;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> payload,
        UUID actorUserId,
        String sourceType,
        UUID sourceId,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPayload(),
                notification.getActor() == null ? null : notification.getActor().getId(),
                notification.getSourceType(),
                notification.getSourceId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}

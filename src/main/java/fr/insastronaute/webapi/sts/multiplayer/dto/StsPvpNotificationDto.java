package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StsPvpNotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        UUID actorUserId,
        boolean read,
        Instant createdAt,
        Map<String, Object> payload
) {
}

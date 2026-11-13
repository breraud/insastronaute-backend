package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.UUID;

public record StsPvpChallengeResponse(
        UUID challengeId,
        UUID targetUserId,
        boolean friendly,
        String status,
        Instant expiresAt
) {
}

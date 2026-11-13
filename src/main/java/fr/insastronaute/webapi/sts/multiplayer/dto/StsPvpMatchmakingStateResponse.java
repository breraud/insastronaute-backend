package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.util.UUID;

public record StsPvpMatchmakingStateResponse(
        boolean queued,
        boolean cancelled,
        UUID battleId
) {
}

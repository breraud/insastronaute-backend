package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StsPvpDeckDto(
        UUID id,
        String name,
        String selectedCharacter,
        boolean active,
        List<String> cardIds,
        Instant updatedAt
) {
}

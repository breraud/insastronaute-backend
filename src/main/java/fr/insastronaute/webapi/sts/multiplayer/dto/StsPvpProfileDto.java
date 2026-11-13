package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StsPvpProfileDto(
        UUID profileId,
        UUID userId,
        String selectedCharacter,
        int elo,
        int winsRanked,
        int lossesRanked,
        int winsFriendly,
        int lossesFriendly,
        boolean preferredFriendly,
        Map<String, Integer> characterLevels,
        Instant updatedAt
) {
}

package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.util.List;
import java.util.Map;

public record StsPvpCollectionDto(
        List<String> ownedCardIds,
        List<String> unlockedMultiplayerCardIds,
        Map<String, Integer> characterLevels,
        int minDeckSize,
        int maxDeckSize,
        /** Toutes les cartes sont temporairement jouables, possédées ou non. */
        boolean allCardsUnlocked
) {
}

package fr.insastronaute.webapi.sts.dto;

import java.util.List;

public record StsCompleteNodeRequest(
        String encounterInstanceId,
        String result,
        Integer turnCount,
        Integer playerHpAfter,
        Integer damageTaken,
        List<String> enemiesDefeated,
        String deckHash
) {
}

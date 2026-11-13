package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.util.List;

public record StsPvpDeckSaveRequest(
        String name,
        String selectedCharacter,
        Boolean isActive,
        List<String> cardIds
) {
}

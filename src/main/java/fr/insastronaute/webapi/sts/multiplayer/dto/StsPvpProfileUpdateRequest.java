package fr.insastronaute.webapi.sts.multiplayer.dto;

public record StsPvpProfileUpdateRequest(
        String selectedCharacter,
        Boolean friendMatch
) {
}

package fr.insastronaute.webapi.sts.multiplayer.dto;

public record StsPvpChallengeRequest(
        String targetUserId,
        Boolean friendly
) {
}

package fr.insastronaute.webapi.user.dto;

import jakarta.validation.constraints.NotNull;

public record LeaderboardVisibilityUpdateDto(
        @NotNull(message = "Le statut de visibilité leaderboard est requis.")
        Boolean hiddenFromLeaderboards
) {
}

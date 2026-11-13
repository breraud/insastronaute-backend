package fr.insastronaute.webapi.tournament.dto;

public record TournamentSettlementResultDto(
        int awardedPlayerCount,
        long pointsGranted,
        long pointsRevoked,
        int notificationsSent,
        AdminTournamentDetailDto tournament
) {
}

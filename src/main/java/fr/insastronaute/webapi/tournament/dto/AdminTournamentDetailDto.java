package fr.insastronaute.webapi.tournament.dto;

import java.util.List;

public record AdminTournamentDetailDto(
        AdminTournamentSummaryDto tournament,
        List<TournamentParticipantDto> participants,
        List<TournamentVictoryTierDto> victoryTiers
) {
}

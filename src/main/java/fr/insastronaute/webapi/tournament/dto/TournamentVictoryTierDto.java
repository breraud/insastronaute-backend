package fr.insastronaute.webapi.tournament.dto;

import java.util.List;
import java.util.UUID;

public record TournamentVictoryTierDto(
        int position,
        String label,
        int points,
        String teamName,
        List<UUID> memberUserIds
) {
}

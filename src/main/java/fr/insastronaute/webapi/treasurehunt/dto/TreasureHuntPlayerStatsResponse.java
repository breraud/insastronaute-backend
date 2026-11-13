package fr.insastronaute.webapi.treasurehunt.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La chasse d'un joueur, pour sa fiche dans le panneau d'administration. */
public record TreasureHuntPlayerStatsResponse(
        UUID userId,
        Instant firstValidatedAt,
        Instant vaultUnlockedAt,
        List<TreasureHuntPlayerPathStatsResponse> paths
) { }

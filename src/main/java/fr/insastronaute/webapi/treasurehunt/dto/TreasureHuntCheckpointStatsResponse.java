package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;

/**
 * Ce que la chasse a donne jusqu'ici, etape par etape.
 *
 * Seules les reussites sont comptees : un essai refuse ne laisse aucune trace en
 * base, et c'est voulu -- le refus commun ne doit rien apprendre a personne, pas
 * meme a une table.
 */
public record TreasureHuntCheckpointStatsResponse(
        long participantCount,
        long vaultUnlockCount,
        List<TreasureHuntPathStatsResponse> paths
) { }

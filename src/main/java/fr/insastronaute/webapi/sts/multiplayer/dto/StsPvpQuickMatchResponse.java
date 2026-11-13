package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.util.UUID;

/**
 * @param mode          le format de la recherche, renvoye pour que le client sache dans quelle
 *                      file il attend
 * @param queuedPlayers combien de joueurs sont deja dans cette file, celui qui demande compris
 * @param neededPlayers combien il en faut pour ouvrir un combat de ce format
 */
public record StsPvpQuickMatchResponse(
        boolean queued,
        boolean matched,
        UUID battleId,
        String message,
        String mode,
        int queuedPlayers,
        int neededPlayers
) {

    /** La forme historique, pour les appels qui n'ont rien a dire du format. */
    public StsPvpQuickMatchResponse(boolean queued, boolean matched, UUID battleId, String message) {
        this(queued, matched, battleId, message, "ONE_V_ONE", 0, 2);
    }
}

package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;
import java.util.UUID;

/** Les joueurs qui ont valide une etape, dans l'ordre ou ils sont arrives. */
public record TreasureHuntCheckpointPlayersResponse(
        UUID checkpointId,
        UUID pathId,
        int stepNumber,
        List<TreasureHuntCheckpointPlayerResponse> players
) { }

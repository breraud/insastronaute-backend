package fr.insastronaute.webapi.treasurehunt.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Un joueur passe par une etape. Le delai court depuis sa propre validation de
 * l'etape precedente ; il est nul pour la premiere etape, ou si l'etape precedente
 * n'a pas de validation a son nom.
 */
public record TreasureHuntCheckpointPlayerResponse(
        UUID userId,
        String displayName,
        String departmentShortName,
        Instant validatedAt,
        Long secondsFromPreviousStep
) { }

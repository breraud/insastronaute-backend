package fr.insastronaute.webapi.treasurehunt.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une etape et ses validations.
 *
 * Le delai se mesure depuis l'etape precedente, joueur par joueur, et seule sa
 * mediane sort : une moyenne se laisserait tirer par le joueur revenu trois jours
 * plus tard. Il est nul pour la premiere etape, que rien ne precede.
 */
public record TreasureHuntStepStatsResponse(
        UUID checkpointId,
        int stepNumber,
        long validatedCount,
        Instant firstValidatedAt,
        Instant lastValidatedAt,
        Long medianSecondsFromPreviousStep
) { }

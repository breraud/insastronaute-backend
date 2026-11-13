package fr.insastronaute.webapi.treasurehunt.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La progression d'un joueur sur une voie.
 *
 * La date d'emission du code du coffre dit que la voie est terminee ; le code
 * lui-meme ne sort pas. Il reste un secret du joueur, et l'administration n'a pas
 * a le lire pour savoir ou il en est.
 */
public record TreasureHuntPlayerPathStatsResponse(
        UUID pathId,
        int position,
        String name,
        String departmentShortName,
        int totalSteps,
        long validatedSteps,
        Instant vaultCodeIssuedAt,
        List<TreasureHuntValidatedStepResponse> steps
) { }

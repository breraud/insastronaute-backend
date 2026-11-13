package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.UUID;
import java.util.List;

/**
 * Ce qu'une cle validee rend au joueur.
 *
 * Sans la position de la voie : une etape par voie suffisait sinon a reconstituer
 * la combinaison du coffre, une reponse de validation a la fois.
 */
public record CheckpointValidationResponse(
        UUID pathId,
        Integer stepNumber,
        List<TreasureHuntContentResponse> contents,
        String vaultCode
) { }

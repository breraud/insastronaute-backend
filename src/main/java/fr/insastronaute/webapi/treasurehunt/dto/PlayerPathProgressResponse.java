package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.UUID;

/**
 * La progression d'un joueur sur une voie.
 *
 * Sans sa position : celle-ci est l'ordre dans lequel le coffre attend les six
 * codes, et le joueur la tient deja de son carnet. La lui rendre ici revenait a
 * ecrire la combinaison dans la reponse, a cote des codes eux-memes.
 */
public record PlayerPathProgressResponse(
        UUID pathId,
        String department,
        long validatedStepCount,
        String vaultCode
) { }

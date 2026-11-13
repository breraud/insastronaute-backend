package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

/**
 * Ce qui empêche — ou non — d'inviter quelqu'un sur un défi.
 *
 * Le refus existait déjà, mais seulement au moment d'envoyer la preuve, une fois
 * l'équipe formée et la photo prise. Le dire à la sélection évite de découvrir
 * en fin de parcours qu'un coéquipier n'était pas éligible.
 *
 * @param reason phrase à afficher telle quelle, {@code null} si éligible
 */
public record ParticipantEligibilityDto(
        UUID userId,
        String displayName,
        boolean eligible,
        String reason
) {
}

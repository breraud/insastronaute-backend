package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

/**
 * Ce qu'une suppression de defi emporterait.
 *
 * <p>La confirmation disait « cette action est definitive » et rien d'autre : le
 * meme avertissement pour un defi que personne n'a releve et pour un defi qui a
 * credite trois cents joueurs. Ces deux nombres sont ce qui manquait.</p>
 *
 * @param creditedPlayers joueurs qui perdent les points de ce defi -- le chiffre
 *                        qui compte, car il se lit dans le classement dès la
 *                        suppression
 * @param submissions     preuves envoyees, validees ou non, qui seront effacees
 */
public record ChallengeDeletionImpactDto(
        UUID challengeId,
        String challengeTitle,
        int pointsReward,
        long creditedPlayers,
        long submissions
) {
}

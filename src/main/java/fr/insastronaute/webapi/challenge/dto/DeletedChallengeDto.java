package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne de la corbeille des defis.
 *
 * <p>Un defi supprime n'est pas efface : il est marque. Cette liste est ce qui
 * permet de le retrouver, et chaque ligne porte ce qu'une restauration rendrait --
 * c'est la seule question qu'on se pose devant elle.</p>
 *
 * @param deletedBy       le nom de qui a supprime, nul si sa ligne n'existe plus
 * @param creditedPlayers joueurs qui retrouveraient les points de ce defi
 * @param submissions     preuves conservees, validees ou non
 */
public record DeletedChallengeDto(
        UUID challengeId,
        String challengeTitle,
        int pointsReward,
        Instant deletedAt,
        String deletedBy,
        long creditedPlayers,
        long submissions
) {
}

package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une soumission du joueur, telle qu'il la relit dans sa page des envois.
 *
 * <p>Le titre du defi voyage avec la ligne parce que la page doit s'afficher seule :
 * un defi depublie ou hors de la fenetre du joueur peut ne plus figurer dans
 * {@code /api/challenges}, et la ligne serait alors anonyme.</p>
 *
 * <p>{@code pointsReward + bonusPoints} n'est pas toujours ce que le joueur touche :
 * {@code promo} verse au departement, {@code rewardShared} divise entre les membres,
 * et {@code rewarded} faux signifie que le quota du defi etait deja atteint. Sans ces
 * trois drapeaux, l'ecran dont le metier est d'annoncer un gain annoncerait un nombre
 * que le classement contredit.</p>
 */
public record SubmissionResultDto(
        UUID submissionId,
        UUID challengeId,
        String challengeTitle,
        String status,
        Instant submittedAt,
        Instant validatedAt,
        Integer pointsReward,
        /** Signe : un malus est une valeur negative. */
        Integer bonusPoints,
        /** Un gain automatique : aucune preuve envoyee, aucune equipe, aucune discussion. */
        boolean automatic,
        boolean promo,
        boolean rewardShared,
        /** Faux quand le quota du defi avait deja absorbe la recompense. */
        boolean rewarded,
        String moderationComment,
        boolean participantOnly,
        /**
         * La reponse du joueur a son invitation : ACCEPTED, DECLINED, ou PENDING.
         *
         * <p>Null quand il est l'initiateur -- il n'a personne a qui repondre. Sans
         * cette information, la notification d'invitation reaffichait ses deux boutons
         * apres coup, et un second clic partait dans le vide.</p>
         */
        String myConfirmationStatus
) {
}

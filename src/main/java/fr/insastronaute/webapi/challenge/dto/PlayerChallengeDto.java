package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlayerChallengeDto(
        UUID id,
        String title,
        String description,
        Integer pointsReward,
        String category,
        /** Toutes les catégories du défi, la principale en tête. */
        List<String> categories,
        String validationMode,
        /**
         * Ce que le défi attend comme preuve : MEDIA ou TEXT.
         *
         * <p>Le formulaire de l'application le lisait déjà, mais rien ne l'envoyait :
         * toujours indéfini, il proposait donc le dépôt de fichiers même pour un défi
         * qui n'attend qu'une phrase.</p>
         */
        String proofType,
        Boolean sensitive,
        /**
         * Un defi de promo : le departement le releve une fois, et ses points lui
         * reviennent. L'application le dit au joueur, qui verrait sinon un defi
         * « deja fait » sans l'avoir fait.
         */
        boolean promo,
        Integer minGroupSize,
        Integer maxGroupSize,

        int userCompletionsCount,     // 0 ou 1 : chaque défi est validable une seule fois
        boolean hasPendingSubmission, // A-t-il une preuve en attente de lecture par un admin ?
        boolean canStillComplete,     // true s'il n'est ni déjà validé, ni en attente
        boolean completed,
        boolean favorite,

        /** Début de validité. Null = disponible dès parution. */
        Instant startDate,
        /** Fin de validité, pour signaler ce qui va expirer. Null = sans échéance. */
        Instant endDate,
        /**
         * Jusqu'à quand une grâce rouvre ce défi fermé, ou {@code null}.
         *
         * <p>Sans elle, le joueur gracié voit un défi qui redevient soumettable
         * sans savoir pourquoi ni jusqu'à quand — et repousse au lendemain un
         * délai qui se compte en heures.</p>
         */
        Instant graceUntil
) {}

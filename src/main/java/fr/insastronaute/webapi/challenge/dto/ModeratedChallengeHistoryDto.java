package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModeratedChallengeHistoryDto(
        UUID id,
        Instant occurredAt,
        ModerationHistoryType type,
        Party actor,
        boolean actorHidden,
        Party player,
        List<Party> participants,
        String challengeTitle,
        Integer points,
        Integer bonusPoints,
        /**
         * Le bonus d'avant, sur un ajustement : sans lui, la ligne dit « +30 » sans
         * dire si c'est un cadeau ou la moitie de ce qui avait ete promis.
         */
        Integer previousBonusPoints,
        String message,
        UUID submissionId,
        UUID challengeId,
        /**
         * Le defi n'existe plus.
         *
         * <p>La ligne reste -- titre, points, participants sont dans son propre JSON,
         * elle se lit sans le defi. Mais les gestes qu'elle propose, une grace ou un
         * ajustement de bonus, n'ont plus de cible : sans ce drapeau l'ecran les offre
         * quand meme, et ils partent en 404.</p>
         */
        boolean challengeDeleted,
        /** La soumission n'existe plus, seule ou avec son defi. */
        boolean submissionDeleted,
        boolean backfilled
) {
    public record Party(UUID id, String displayName) {}
}

package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

/**
 * Ce que voit celui qui vient de scanner, avant de dire oui.
 *
 * @param reason pourquoi il ne peut pas rejoindre, quand c'est le cas : dit avec
 *               ses mots, et non « invitation invalide »
 */
public record GroupInvitePreviewDto(
        UUID challengeId,
        String challengeTitle,
        String submitterName,
        boolean canJoin,
        boolean alreadyMember,
        String reason
) {
}

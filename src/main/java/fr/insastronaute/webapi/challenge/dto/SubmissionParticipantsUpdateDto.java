package fr.insastronaute.webapi.challenge.dto;

import java.util.List;
import java.util.UUID;

/**
 * L'equipe telle que le chef vient de la composer.
 *
 * <p>La liste complete, et non les changements : le chef ajoute et retire dans sa
 * page, puis confirme. Envoyer geste par geste ferait subir au groupe des etats
 * que personne ne voulait -- notamment un envoi declenche par le retrait du
 * dernier membre en attente.</p>
 */
public record SubmissionParticipantsUpdateDto(List<UUID> participantUserIds) {
}

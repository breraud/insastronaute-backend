package fr.insastronaute.webapi.cardteam.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une invitation qu'on a envoyee et qui attend encore une reponse.
 *
 * <p>Sans elle, celui qui propose de fonder une equipe n'a aucune trace de sa demande :
 * rien n'a ete cree, et il ne verrait que l'ecran d'avant. Il la renverrait, pour se
 * heurter au refus qui protege l'invite d'invitations concurrentes.</p>
 */
public record CardTeamOutgoingInvitationDto(
        UUID id,
        String teamName,
        CardTeamUserDto invited,
        Instant createdAt
) {
}

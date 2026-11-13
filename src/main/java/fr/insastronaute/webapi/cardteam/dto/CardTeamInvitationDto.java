package fr.insastronaute.webapi.cardteam.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une invitation, vers une equipe existante ou vers une equipe a fonder.
 *
 * <p>Exactement l'un des deux est renseigne : {@code team} quand l'invitant a deja une
 * equipe, {@code proposedTeamName} quand l'invitation propose d'en fonder une a deux.</p>
 */
public record CardTeamInvitationDto(
        UUID id,
        CardTeamSummaryDto team,
        String proposedTeamName,
        CardTeamUserDto inviter,
        Instant createdAt
) {
}

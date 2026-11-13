package fr.insastronaute.webapi.cardteam.dto;

import jakarta.validation.constraints.Size;

/**
 * Le nom que portera l'equipe fondee par cette invitation.
 *
 * <p>Absent lorsque l'invitant a deja une equipe : elle est deja nommee.</p>
 */
public record CardTeamInviteRequest(
        @Size(max = CardTeamNames.MAX_LENGTH, message = "Le nom de l'equipe doit contenir au plus 18 caracteres.")
        String teamName
) {
}

package fr.insastronaute.webapi.card.dto;

import java.time.Instant;

/** Un exemplaire papier du joueur, par le code imprime dessus : c'est ainsi qu'il le reconnait. */
public record OwnedCardInstanceDto(String publicId, Instant assignedAt) {
}

package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotNull;

public record CollectionTestModeRequest(
        @NotNull(message = "Le mode de test est requis.")
        Boolean allCardsOwned
) {
}

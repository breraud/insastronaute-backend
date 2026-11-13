package fr.insastronaute.webapi.deck.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import fr.insastronaute.webapi.deck.domain.DeckKind;

import java.util.List;

public record DeckCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @PositiveOrZero Long price,
        @Positive Integer cardsPerPull,
        // Cadrage du recto dans l'emballage à plat. Absents = centre, sans agrandissement.
        Double frontXPercent,
        Double frontZoom,
        DeckKind kind,
        @NotEmpty @Valid List<DropTierCreateRequest> dropTiers
) {
}

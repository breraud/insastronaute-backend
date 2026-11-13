package fr.insastronaute.webapi.deck.dto;

import fr.insastronaute.webapi.deck.domain.DeckKind;

import java.util.UUID;

public record DeckSummaryDto(
        UUID id,
        String name,
        Long price,
        Integer cardsPerPull,
        String imageUrl,
        Double frontXPercent,
        Double frontZoom,
        DeckKind kind
) {
}

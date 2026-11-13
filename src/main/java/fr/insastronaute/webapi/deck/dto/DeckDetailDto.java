package fr.insastronaute.webapi.deck.dto;

import fr.insastronaute.webapi.deck.domain.DeckKind;

import java.util.List;
import java.util.UUID;

public record DeckDetailDto(
        UUID id,
        String name,
        Long price,
        Integer cardsPerPull,
        String imageUrl,
        Double frontXPercent,
        Double frontZoom,
        DeckKind kind,
        List<DeckCategoryImageDto> categoryImages,
        List<DropTierDto> dropTiers
) {
}

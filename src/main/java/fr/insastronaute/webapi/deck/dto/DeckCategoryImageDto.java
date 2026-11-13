package fr.insastronaute.webapi.deck.dto;

import java.util.UUID;

public record DeckCategoryImageDto(
        UUID categoryId,
        String category,
        String imageUrl,
        String thumbnailUrl
) {
}

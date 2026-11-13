package fr.insastronaute.webapi.card.dto;

import java.util.List;
import java.util.UUID;

public record CardDeckCategoryDto(
        UUID categoryId,
        String category,
        String imageUrl,
        String thumbnailUrl,
        long ownedCount,
        long totalCount,
        List<CardDeckCardDto> cards
) {
}

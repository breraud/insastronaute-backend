package fr.insastronaute.webapi.card.dto;

import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import java.util.List;

public record CardDeckDto(
        long ownedCount,
        long totalCount,
        List<CardDeckCategoryDto> categories,
        CardCollectionScope scope,
        CardDeckTeamDto team,
        Long teamScore
) {
    public CardDeckDto(long ownedCount, long totalCount, List<CardDeckCategoryDto> categories) {
        this(ownedCount, totalCount, categories, CardCollectionScope.PERSONAL, null, null);
    }
}

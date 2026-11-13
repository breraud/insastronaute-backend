package fr.insastronaute.webapi.card.dto;

import fr.insastronaute.webapi.card.domain.CardRevealAnimation;

import java.time.Instant;
import java.util.UUID;

public record CardDto(
        UUID id,
        String name,
        UUID categoryId,
        String category,
        UUID typeId,
        String type,
        boolean enabled,
        boolean defaultCard,
        int displayOrder,
        String imageUrl,
        String textlessImageUrl,
        String thumbnailUrl,
        String contentType,
        Integer firstAcquisitionPoints,
        Integer duplicatePoints,
        int effectiveFirstAcquisitionPoints,
        int effectiveDuplicatePoints,
        /** La mise en scène de sa catégorie à l'ouverture d'un pack. */
        CardRevealAnimation revealAnimation,
        Instant createdAt,
        Instant updatedAt
) {
    public CardDto(
            UUID id, String name, UUID categoryId, String category,
            boolean enabled, boolean defaultCard, int displayOrder,
            String imageUrl, String textlessImageUrl, String thumbnailUrl, String contentType,
            Instant createdAt, Instant updatedAt
    ) {
        this(id, name, categoryId, category, null, null, enabled, defaultCard,
                displayOrder, imageUrl, textlessImageUrl, thumbnailUrl, contentType, null, null, 0, 0,
                CardRevealAnimation.AUTO, createdAt, updatedAt);
    }

    public CardDto(
            UUID id, String name, UUID categoryId, String category,
            UUID typeId, String type, boolean enabled, boolean defaultCard, int displayOrder,
            String imageUrl, String textlessImageUrl, String thumbnailUrl, String contentType,
            Instant createdAt, Instant updatedAt
    ) {
        this(id, name, categoryId, category, typeId, type, enabled, defaultCard,
                displayOrder, imageUrl, textlessImageUrl, thumbnailUrl, contentType, null, null, 0, 0,
                CardRevealAnimation.AUTO, createdAt, updatedAt);
    }
}

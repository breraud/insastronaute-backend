package fr.insastronaute.webapi.card.dto;

import fr.insastronaute.webapi.card.domain.CardRevealAnimation;

import java.time.Instant;
import java.util.UUID;

public record CardCategoryDto(
        UUID id,
        String name,
        int displayOrder,
        String imageUrl,
        String thumbnailUrl,
        String contentType,
        boolean hidden,
        boolean game,
        boolean extension,
        int firstAcquisitionPoints,
        int duplicatePoints,
        int duplicatesPerPoint,
        CardRevealAnimation revealAnimation,
        Instant createdAt,
        Instant updatedAt
) {
    public CardCategoryDto(
            UUID id, String name, int displayOrder, String imageUrl, String thumbnailUrl,
            String contentType, boolean hidden, boolean game, int firstAcquisitionPoints,
            int duplicatePoints, Instant createdAt, Instant updatedAt
    ) {
        this(id, name, displayOrder, imageUrl, thumbnailUrl, contentType, hidden, game, false,
                firstAcquisitionPoints, duplicatePoints, 1, CardRevealAnimation.AUTO, createdAt, updatedAt);
    }

    public CardCategoryDto(
            UUID id,
            String name,
            int displayOrder,
            String imageUrl,
            String thumbnailUrl,
            String contentType,
            boolean hidden,
            boolean game,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, displayOrder, imageUrl, thumbnailUrl, contentType, hidden, game, false, 0, 0, 1,
                CardRevealAnimation.AUTO, createdAt, updatedAt);
    }
}

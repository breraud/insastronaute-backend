package fr.insastronaute.webapi.card.dto;

import java.util.UUID;

public record CardDeckCardDto(
        UUID id,
        String name,
        UUID categoryId,
        String category,
        UUID typeId,
        String type,
        boolean defaultCard,
        int quantity,
        int displayOrder,
        String imageUrl,
        String thumbnailUrl,
        Integer firstAcquisitionPoints,
        Integer duplicatePoints,
        /** Doublons nécessaires pour marquer duplicatePoints : 10 pour une commune. */
        Integer duplicatesPerPoint,
        /** PC que cette carte rapporte réellement au joueur aujourd'hui, exemplaires compris. */
        Long collectionPoints
) {
    public CardDeckCardDto(
            UUID id, String name, UUID categoryId, String category,
            boolean defaultCard, int quantity, int displayOrder,
            String imageUrl, String thumbnailUrl
    ) {
        this(id, name, categoryId, category, null, null, defaultCard, quantity,
                displayOrder, imageUrl, thumbnailUrl, null, null, null, null);
    }
}

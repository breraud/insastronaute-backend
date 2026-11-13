package fr.insastronaute.webapi.challenge.auto.types.cardcategorycompletion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

import java.util.UUID;

public record CardCategoryCompletionParameters(
        UUID categoryId, int percentage, CardCollectionType collectionType
) {

    @JsonCreator
    public CardCategoryCompletionParameters(
            @JsonProperty("categoryId") UUID categoryId,
            @JsonProperty("percentage") Integer percentage,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(requireCategory(categoryId), normalize(percentage), CollectionScope.parse(collectionType));
    }

    private static UUID requireCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La catégorie de carte est requise.");
        }
        return categoryId;
    }

    private static int normalize(Integer percentage) {
        int value = percentage == null ? 100 : percentage;
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être compris entre 1 et 100.");
        }
        return value;
    }
}

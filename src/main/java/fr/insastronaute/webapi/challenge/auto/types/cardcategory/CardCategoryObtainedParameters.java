package fr.insastronaute.webapi.challenge.auto.types.cardcategory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

import java.util.UUID;

public record CardCategoryObtainedParameters(UUID categoryId, int count, CardCollectionType collectionType) {

    @JsonCreator
    public CardCategoryObtainedParameters(
            @JsonProperty("categoryId") UUID categoryId,
            @JsonProperty("count") Integer count,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(requireCategory(categoryId), normalizeCount(count), CollectionScope.parse(collectionType));
    }

    private static UUID requireCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La catégorie de carte est requise.");
        }
        return categoryId;
    }

    private static int normalizeCount(Integer count) {
        int value = count == null ? 1 : count;
        if (value < 1) {
            throw new IllegalArgumentException("Le nombre de cartes doit être au moins 1.");
        }
        return value;
    }
}

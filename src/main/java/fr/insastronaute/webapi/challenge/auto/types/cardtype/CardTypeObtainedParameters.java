package fr.insastronaute.webapi.challenge.auto.types.cardtype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

import java.util.UUID;

public record CardTypeObtainedParameters(UUID typeId, int count, CardCollectionType collectionType) {

    @JsonCreator
    public CardTypeObtainedParameters(
            @JsonProperty("typeId") UUID typeId,
            @JsonProperty("count") Integer count,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(requireType(typeId), normalizeCount(count), CollectionScope.parse(collectionType));
    }

    private static UUID requireType(UUID typeId) {
        if (typeId == null) {
            throw new IllegalArgumentException("Le type de carte est requis.");
        }
        return typeId;
    }

    private static int normalizeCount(Integer count) {
        int value = count == null ? 1 : count;
        if (value < 1) {
            throw new IllegalArgumentException("Le nombre de cartes doit être au moins 1.");
        }
        return value;
    }
}

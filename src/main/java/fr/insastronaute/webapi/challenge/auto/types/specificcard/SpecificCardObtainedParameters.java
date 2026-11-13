package fr.insastronaute.webapi.challenge.auto.types.specificcard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

import java.util.UUID;

public record SpecificCardObtainedParameters(UUID cardId, CardCollectionType collectionType) {

    @JsonCreator
    public SpecificCardObtainedParameters(
            @JsonProperty("cardId") UUID cardId,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(requireCard(cardId), CollectionScope.parse(collectionType));
    }

    private static UUID requireCard(UUID cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("La carte est requise.");
        }
        return cardId;
    }
}

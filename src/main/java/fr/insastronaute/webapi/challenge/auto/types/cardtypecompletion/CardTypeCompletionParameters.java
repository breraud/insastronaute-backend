package fr.insastronaute.webapi.challenge.auto.types.cardtypecompletion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

import java.util.UUID;

public record CardTypeCompletionParameters(UUID typeId, int percentage, CardCollectionType collectionType) {

    @JsonCreator
    public CardTypeCompletionParameters(
            @JsonProperty("typeId") UUID typeId,
            @JsonProperty("percentage") Integer percentage,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(requireType(typeId), normalize(percentage), CollectionScope.parse(collectionType));
    }

    private static UUID requireType(UUID typeId) {
        if (typeId == null) {
            throw new IllegalArgumentException("Le type de carte est requis.");
        }
        return typeId;
    }

    /** Cent par défaut : « toutes les cartes bar » est la phrase qu'on écrit le plus. */
    private static int normalize(Integer percentage) {
        int value = percentage == null ? 100 : percentage;
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être compris entre 1 et 100.");
        }
        return value;
    }
}

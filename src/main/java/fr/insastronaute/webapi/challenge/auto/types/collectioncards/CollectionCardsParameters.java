package fr.insastronaute.webapi.challenge.auto.types.collectioncards;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

/** Un nombre de cartes distinctes, sur la collection entiere. */
public record CollectionCardsParameters(int count, CardCollectionType collectionType) {

    @JsonCreator
    public CollectionCardsParameters(
            @JsonProperty("count") Integer count,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(normalize(count), CollectionScope.parse(collectionType));
    }

    private static int normalize(Integer count) {
        int value = count == null ? 1 : count;
        if (value < 1) {
            throw new IllegalArgumentException("Le nombre de cartes doit être au moins 1.");
        }
        return value;
    }
}

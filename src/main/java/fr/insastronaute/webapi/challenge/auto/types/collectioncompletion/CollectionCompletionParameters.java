package fr.insastronaute.webapi.challenge.auto.types.collectioncompletion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

/** Une part de la collection entiere, en pourcentage. */
public record CollectionCompletionParameters(int percentage, CardCollectionType collectionType) {

    @JsonCreator
    public CollectionCompletionParameters(
            @JsonProperty("percentage") Integer percentage,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(normalize(percentage), CollectionScope.parse(collectionType));
    }

    private static int normalize(Integer percentage) {
        int value = percentage == null ? 100 : percentage;
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être compris entre 1 et 100.");
        }
        return value;
    }
}

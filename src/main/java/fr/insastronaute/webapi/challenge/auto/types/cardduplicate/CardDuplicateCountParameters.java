package fr.insastronaute.webapi.challenge.auto.types.cardduplicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;

public record CardDuplicateCountParameters(int copies, CardCollectionType collectionType) {

    @JsonCreator
    public CardDuplicateCountParameters(
            @JsonProperty("copies") Integer copies,
            @JsonProperty("collectionType") String collectionType
    ) {
        this(normalize(copies), CollectionScope.parse(collectionType));
    }

    private static int normalize(Integer copies) {
        int value = copies == null ? 2 : copies;
        if (value < 2) {
            throw new IllegalArgumentException("Le nombre d'exemplaires doit être au moins 2.");
        }
        return value;
    }
}

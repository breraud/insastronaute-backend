package fr.insastronaute.webapi.challenge.auto.types.stspvpwins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StsPvpWinsParameters(int count, boolean includeFriendly) {

    @JsonCreator
    public StsPvpWinsParameters(
            @JsonProperty("count") Integer count,
            @JsonProperty("includeFriendly") Boolean includeFriendly
    ) {
        this(normalize(count), includeFriendly != null && includeFriendly);
    }

    private static int normalize(Integer count) {
        int value = count == null ? 1 : count;
        if (value < 1) {
            throw new IllegalArgumentException("Le nombre de victoires doit être au moins 1.");
        }
        return value;
    }
}

package fr.insastronaute.webapi.challenge.auto.types.friendscount;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FriendsCountParameters(int count) {

    @JsonCreator
    public FriendsCountParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer value) {
        int normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre d'amis doit être au moins 1.");
        }
        return normalized;
    }
}

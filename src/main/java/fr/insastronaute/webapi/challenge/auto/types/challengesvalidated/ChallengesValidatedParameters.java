package fr.insastronaute.webapi.challenge.auto.types.challengesvalidated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ChallengesValidatedParameters(int count) {

    @JsonCreator
    public ChallengesValidatedParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer value) {
        int normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre de défis validés doit être au moins 1.");
        }
        return normalized;
    }
}

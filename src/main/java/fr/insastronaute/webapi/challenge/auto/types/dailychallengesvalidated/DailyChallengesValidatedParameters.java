package fr.insastronaute.webapi.challenge.auto.types.dailychallengesvalidated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DailyChallengesValidatedParameters(int count) {

    @JsonCreator
    public DailyChallengesValidatedParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer value) {
        int normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre de défis du jour validés doit être au moins 1.");
        }
        return normalized;
    }
}

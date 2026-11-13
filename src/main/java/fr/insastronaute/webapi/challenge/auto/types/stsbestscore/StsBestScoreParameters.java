package fr.insastronaute.webapi.challenge.auto.types.stsbestscore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StsBestScoreParameters(long score) {

    @JsonCreator
    public StsBestScoreParameters(@JsonProperty("score") Long score) {
        this(normalize(score));
    }

    private static long normalize(Long value) {
        long normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Score à atteindre doit être au moins 1.");
        }
        return normalized;
    }
}

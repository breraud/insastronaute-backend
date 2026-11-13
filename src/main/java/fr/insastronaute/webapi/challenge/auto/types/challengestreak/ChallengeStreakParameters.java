package fr.insastronaute.webapi.challenge.auto.types.challengestreak;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ChallengeStreakParameters(int days) {

    @JsonCreator
    public ChallengeStreakParameters(@JsonProperty("days") Integer days) {
        this(normalize(days));
    }

    private static int normalize(Integer days) {
        int value = days == null ? 2 : days;
        if (value < 2) {
            throw new IllegalArgumentException("Une série doit couvrir au moins 2 jours.");
        }
        return value;
    }
}

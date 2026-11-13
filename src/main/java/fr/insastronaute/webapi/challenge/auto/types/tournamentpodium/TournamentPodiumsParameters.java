package fr.insastronaute.webapi.challenge.auto.types.tournamentpodium;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TournamentPodiumsParameters(int count) {

    @JsonCreator
    public TournamentPodiumsParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer value) {
        int normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre de podiums doit être au moins 1.");
        }
        return normalized;
    }
}

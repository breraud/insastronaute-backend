package fr.insastronaute.webapi.challenge.auto.types.stspvpelo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StsPvpEloParameters(int elo) {

    @JsonCreator
    public StsPvpEloParameters(@JsonProperty("elo") Integer elo) {
        this(normalize(elo));
    }

    private static int normalize(Integer elo) {
        // New profiles start at 1000, so anything at or below that would unlock
        // for every player who has simply opened the duel screen.
        int value = elo == null ? 1001 : elo;
        if (value <= 1000) {
            throw new IllegalArgumentException("L'elo visé doit dépasser 1000, le score de départ.");
        }
        return value;
    }
}

package fr.insastronaute.webapi.challenge.auto.types.treasurecheckpoints;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TreasureCheckpointsParameters(int count) {

    @JsonCreator
    public TreasureCheckpointsParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer value) {
        int normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre de checkpoints doit être au moins 1.");
        }
        return normalized;
    }
}

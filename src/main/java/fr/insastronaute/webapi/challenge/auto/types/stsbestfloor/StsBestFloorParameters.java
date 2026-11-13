package fr.insastronaute.webapi.challenge.auto.types.stsbestfloor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StsBestFloorParameters(int floor) {

    @JsonCreator
    public StsBestFloorParameters(@JsonProperty("floor") Integer floor) {
        this(normalize(floor));
    }

    private static int normalize(Integer floor) {
        int value = floor == null ? 1 : floor;
        if (value < 1) {
            throw new IllegalArgumentException("L'étage visé doit être au moins 1.");
        }
        return value;
    }
}

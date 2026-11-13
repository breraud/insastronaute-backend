package fr.insastronaute.webapi.challenge.auto.types.stsrunsfinished;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StsRunsFinishedParameters(long count) {

    @JsonCreator
    public StsRunsFinishedParameters(@JsonProperty("count") Long count) {
        this(normalize(count));
    }

    private static long normalize(Long value) {
        long normalized = value == null ? 1 : value;
        if (normalized < 1) {
            throw new IllegalArgumentException("Nombre de runs terminées doit être au moins 1.");
        }
        return normalized;
    }
}

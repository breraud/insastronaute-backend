package fr.insastronaute.webapi.challenge.auto.types.placebonuspixels;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlaceBonusPixelsParameters(int count) {

    @JsonCreator
    public PlaceBonusPixelsParameters(@JsonProperty("count") Integer count) {
        this(normalize(count));
    }

    private static int normalize(Integer count) {
        int value = count == null ? 1 : count;
        if (value < 1) {
            throw new IllegalArgumentException("Le nombre de pixels bonus doit être au moins 1.");
        }
        return value;
    }
}

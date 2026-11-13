package fr.insastronaute.webapi.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Gender {
    MALE,
    FEMALE,
    NEUTRAL;

    @JsonCreator
    public static Gender from(String value) {
        if (value == null) {
            return null;
        }

        for (Gender gender : values()) {
            if (gender.name().equalsIgnoreCase(value.trim())) {
                return gender;
            }
        }

        throw new IllegalArgumentException("Unsupported gender: " + value);
    }
}

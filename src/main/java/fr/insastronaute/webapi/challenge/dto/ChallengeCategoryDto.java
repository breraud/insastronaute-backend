package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;

public record ChallengeCategoryDto(
        String name,
        boolean defaultCategory,
        boolean systemCategory,
        Instant createdAt,
        Instant updatedAt
) {
    public ChallengeCategoryDto(String name, boolean defaultCategory, Instant createdAt, Instant updatedAt) {
        this(name, defaultCategory, defaultCategory, createdAt, updatedAt);
    }
}

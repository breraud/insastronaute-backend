package fr.insastronaute.webapi.card.dto;

import java.time.Instant;
import java.util.UUID;

public record CardTypeDto(UUID id, String name, Instant createdAt, Instant updatedAt) {
}

package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

public record EventRegistrationDto(
        UUID id,
        EventDto event,
        EventRegistrationStatus status,
        Instant registeredAt
) {
}

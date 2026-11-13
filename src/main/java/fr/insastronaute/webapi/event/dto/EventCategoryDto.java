package fr.insastronaute.webapi.event.dto;

import java.util.UUID;

public record EventCategoryDto(
        UUID id,
        String name,
        String slug
) {
}

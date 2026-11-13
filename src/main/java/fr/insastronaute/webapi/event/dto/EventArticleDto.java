package fr.insastronaute.webapi.event.dto;

import java.util.UUID;

public record EventArticleDto(
        UUID id,
        String title
) {
}

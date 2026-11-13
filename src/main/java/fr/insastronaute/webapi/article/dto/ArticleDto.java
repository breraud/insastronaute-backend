package fr.insastronaute.webapi.article.dto;

import fr.insastronaute.webapi.article.domain.ArticleStatus;

import java.time.Instant;
import java.util.UUID;

public record ArticleDto(
        UUID id,
        String title,
        String contentHtml,
        String coverImageUrl,
        ArticleStatus status,
        CategoryDto category,
        Instant createdAt,
        Instant updatedAt
) {
}

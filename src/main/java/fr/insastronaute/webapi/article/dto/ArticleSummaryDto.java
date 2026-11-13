package fr.insastronaute.webapi.article.dto;

import fr.insastronaute.webapi.article.domain.ArticleStatus;

import java.time.Instant;
import java.util.UUID;

public record ArticleSummaryDto(
        UUID id,
        String title,
        String coverImageUrl,
        ArticleStatus status,
        CategoryDto category,
        Instant createdAt,
        Instant updatedAt
) {
}

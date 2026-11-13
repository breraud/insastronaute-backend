package fr.insastronaute.webapi.article.dto;

import java.time.Instant;
import java.util.UUID;

public record ArticleMediaDto(
        UUID id,
        UUID articleId,
        String s3Url,
        String fileType,
        String originalFileName,
        Instant createdAt
) {
}

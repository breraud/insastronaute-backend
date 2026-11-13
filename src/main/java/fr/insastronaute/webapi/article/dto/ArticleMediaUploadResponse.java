package fr.insastronaute.webapi.article.dto;

import java.util.UUID;

public record ArticleMediaUploadResponse(
        UUID id,
        String url
) {
}

package fr.insastronaute.webapi.article.dto;

import fr.insastronaute.webapi.article.domain.ArticleStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ArticleUpdateRequest(
        @Size(max = 180, message = "Le titre de l'article doit contenir au plus 180 caractères.")
        String title,
        String contentHtml,
        @Size(max = 2048, message = "L'URL de couverture ne peut pas dépasser 2048 caractères.")
        String coverImageUrl,
        UUID categoryId,
        ArticleStatus status
) {
}

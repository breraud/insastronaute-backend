package fr.insastronaute.webapi.article.controller;

import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @Operation(summary = "List published articles", description = "Returns published articles visible to authenticated players.")
    public List<ArticleSummaryDto> listPublishedArticles() {
        return articleService.listPublishedArticles();
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "Get a published article", description = "Returns the full HTML payload for a published article.")
    public ArticleDto getPublishedArticle(@PathVariable UUID articleId) {
        return articleService.getPublishedArticle(articleId);
    }
}

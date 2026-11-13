package fr.insastronaute.webapi.article.controller;

import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleCreateResponse;
import fr.insastronaute.webapi.article.dto.ArticleMediaDto;
import fr.insastronaute.webapi.article.dto.ArticleMediaUploadResponse;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.dto.ArticleUpdateRequest;
import fr.insastronaute.webapi.article.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/articles")
@Tag(name = "Admin Articles")
public class AdminArticleController {

    private final ArticleService articleService;

    public AdminArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @Operation(summary = "List articles", description = "Returns all article drafts and published articles visible to admins.")
    public List<ArticleSummaryDto> listArticles() {
        return articleService.listArticles();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an article", description = "Creates an empty writing article and returns its generated id.")
    public ArticleCreateResponse initializeArticle() {
        return articleService.initializeArticle();
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "Get an article", description = "Returns the full article draft or published article payload for admin editing.")
    public ArticleDto getArticle(@PathVariable UUID articleId) {
        return articleService.getArticle(articleId);
    }

    @PostMapping(path = "/{articleId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload article media", description = "Uploads a validated media file under articles/{articleId}/.")
    public ArticleMediaUploadResponse uploadMedia(
            @PathVariable UUID articleId,
            @RequestParam MultipartFile file
    ) {
        return articleService.uploadMedia(articleId, file);
    }

    @PostMapping(path = "/{articleId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload article cover", description = "Uploads or replaces the public cover image for the article.")
    public ArticleDto uploadCover(
            @PathVariable UUID articleId,
            @RequestParam MultipartFile file
    ) {
        return articleService.uploadCover(articleId, file);
    }

    @GetMapping("/{articleId}/media")
    @Operation(summary = "List article media", description = "Returns every media file registered for the article.")
    public List<ArticleMediaDto> getMedia(@PathVariable UUID articleId) {
        return articleService.getArticleMedia(articleId);
    }

    @PutMapping("/{articleId}")
    @Operation(summary = "Auto-save an article", description = "Stores title, HTML content, and category without changing publication status.")
    public ArticleDto updateArticle(
            @PathVariable UUID articleId,
            @Valid @RequestBody ArticleUpdateRequest request
    ) {
        return articleService.updateArticle(articleId, request);
    }

    @DeleteMapping("/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an article", description = "Deletes the article and its article media directory from public S3.")
    public void deleteArticle(@PathVariable UUID articleId) {
        articleService.deleteArticle(articleId);
    }
}

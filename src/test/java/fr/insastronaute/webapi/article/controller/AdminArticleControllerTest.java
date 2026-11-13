package fr.insastronaute.webapi.article.controller;

import fr.insastronaute.webapi.article.dto.ArticleCreateResponse;
import fr.insastronaute.webapi.article.dto.ArticleMediaDto;
import fr.insastronaute.webapi.article.dto.ArticleMediaUploadResponse;
import fr.insastronaute.webapi.article.domain.ArticleStatus;
import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.dto.CategoryDto;
import fr.insastronaute.webapi.article.service.ArticleService;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanListArticles() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(articleService.listArticles()).thenReturn(List.of(new ArticleSummaryDto(
                articleId,
                "Programme de la semaine",
                "https://cdn.example.com/cover.jpg",
                ArticleStatus.WRITING,
                new CategoryDto(categoryId, "Vie associative", "vie-associative"),
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        )));

        mockMvc.perform(get("/api/admin/articles")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(articleId.toString()))
                .andExpect(jsonPath("$[0].title").value("Programme de la semaine"))
                .andExpect(jsonPath("$[0].status").value("WRITING"))
                .andExpect(jsonPath("$[0].category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].category.name").value("Vie associative"))
                .andExpect(jsonPath("$[0].category.slug").value("vie-associative"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-07-02T08:00:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-07-02T08:10:00Z"));
    }

    @Test
    void adminCanGetArticleForEditing() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(articleService.getArticle(articleId)).thenReturn(new ArticleDto(
                articleId,
                "Programme de la semaine",
                "<h2>Programme</h2><p>Hello</p>",
                "https://cdn.example.com/cover.jpg",
                ArticleStatus.WRITING,
                new CategoryDto(categoryId, "Vie associative", "vie-associative"),
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        ));

        mockMvc.perform(get("/api/admin/articles/{articleId}", articleId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.title").value("Programme de la semaine"))
                .andExpect(jsonPath("$.contentHtml").value("<h2>Programme</h2><p>Hello</p>"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example.com/cover.jpg"))
                .andExpect(jsonPath("$.status").value("WRITING"))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value("Vie associative"))
                .andExpect(jsonPath("$.category.slug").value("vie-associative"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-02T08:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-02T08:10:00Z"));
    }

    @Test
    void adminCanCreateArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.initializeArticle()).thenReturn(new ArticleCreateResponse(articleId));

        mockMvc.perform(post("/api/admin/articles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(articleId.toString()));
    }

    @Test
    void adminCanUploadArticleMedia() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(articleService.uploadMedia(eq(articleId), any())).thenReturn(new ArticleMediaUploadResponse(
                mediaId,
                "https://s3.example.com/insastronaute-public/articles/" + articleId + "/image.png"
        ));

        mockMvc.perform(multipart("/api/admin/articles/{articleId}/media", articleId)
                        .file("file", new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.url").value("https://s3.example.com/insastronaute-public/articles/" + articleId + "/image.png"));
    }

    @Test
    void adminCanUploadArticleCover() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.uploadCover(eq(articleId), any())).thenReturn(new ArticleDto(
                articleId,
                "Programme",
                "<p>Hello</p>",
                "https://s3.example.com/insastronaute-public/articles/" + articleId + "/cover/image.png",
                ArticleStatus.WRITING,
                null,
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        ));

        mockMvc.perform(multipart("/api/admin/articles/{articleId}/cover", articleId)
                        .file("file", new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.coverImageUrl").value("https://s3.example.com/insastronaute-public/articles/" + articleId + "/cover/image.png"));
    }

    @Test
    void adminCanListArticleMedia() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(articleService.getArticleMedia(articleId)).thenReturn(List.of(new ArticleMediaDto(
                mediaId,
                articleId,
                "https://s3.example.com/insastronaute-public/articles/" + articleId + "/image.png",
                "image/png",
                "image.png",
                Instant.parse("2026-07-02T08:02:00Z")
        )));

        mockMvc.perform(get("/api/admin/articles/{articleId}/media", articleId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mediaId.toString()))
                .andExpect(jsonPath("$[0].articleId").value(articleId.toString()))
                .andExpect(jsonPath("$[0].fileType").value("image/png"))
                .andExpect(jsonPath("$[0].originalFileName").value("image.png"));
    }

    @Test
    void adminCanUpdateArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.updateArticle(eq(articleId), any())).thenReturn(new ArticleDto(
                articleId,
                "Programme",
                "<p>Hello</p>",
                "https://cdn.example.com/cover.jpg",
                ArticleStatus.WRITING,
                null,
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        ));

        mockMvc.perform(put("/api/admin/articles/{articleId}", articleId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Programme",
                                  "contentHtml": "<p>Hello</p>",
                                  "coverImageUrl": "https://cdn.example.com/cover.jpg",
                                  "categoryId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.title").value("Programme"))
                .andExpect(jsonPath("$.contentHtml").value("<p>Hello</p>"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example.com/cover.jpg"));
    }

    @Test
    void adminCanDeleteArticle() throws Exception {
        mockMvc.perform(delete("/api/admin/articles/{articleId}", UUID.randomUUID())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
    }

    @Test
    void getArticleReturnsNotFoundForUnknownArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.getArticle(articleId)).thenThrow(new NotFoundException("Article introuvable."));

        mockMvc.perform(get("/api/admin/articles/{articleId}", articleId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Article introuvable."));
    }

    @Test
    void studentCannotListAdminArticles() throws Exception {
        mockMvc.perform(get("/api/admin/articles")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(articleService);
    }

    @Test
    void studentCannotGetArticleForEditing() throws Exception {
        mockMvc.perform(get("/api/admin/articles/{articleId}", UUID.randomUUID())
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(articleService);
    }

    @Test
    void studentCannotDeleteArticles() throws Exception {
        mockMvc.perform(delete("/api/admin/articles/{articleId}", UUID.randomUUID())
                        .with(user("student").roles("STUDENT"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(articleService);
    }

    @Test
    void publicArticlePrefixDoesNotAcceptWrites() throws Exception {
        mockMvc.perform(post("/api/articles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(articleService);
    }
}

package fr.insastronaute.webapi.article.controller;

import fr.insastronaute.webapi.article.domain.ArticleStatus;
import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void authenticatedUserCanListPublishedArticles() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.listPublishedArticles()).thenReturn(List.of(new ArticleSummaryDto(
                articleId,
                "Programme de la semaine",
                "https://cdn.example.com/cover.jpg",
                ArticleStatus.PUBLISHED,
                null,
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        )));

        mockMvc.perform(get("/api/articles")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(articleId.toString()))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));

        verify(articleService).listPublishedArticles();
    }

    @Test
    void authenticatedUserCanReadPublishedArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(articleService.getPublishedArticle(articleId)).thenReturn(new ArticleDto(
                articleId,
                "Programme de la semaine",
                "<p>Hello</p>",
                "https://cdn.example.com/cover.jpg",
                ArticleStatus.PUBLISHED,
                null,
                Instant.parse("2026-07-02T08:00:00Z"),
                Instant.parse("2026-07-02T08:10:00Z")
        ));

        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cdn.example.com/cover.jpg"))
                .andExpect(jsonPath("$.contentHtml").value("<p>Hello</p>"));

        verify(articleService).getPublishedArticle(articleId);
    }
}

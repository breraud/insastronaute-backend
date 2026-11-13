package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.article.domain.ArticleMedia;
import fr.insastronaute.webapi.article.domain.ArticleStatus;
import fr.insastronaute.webapi.article.domain.Category;
import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleMediaUploadResponse;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.dto.ArticleUpdateRequest;
import fr.insastronaute.webapi.article.repository.ArticleMediaRepository;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.article.repository.CategoryRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleMediaRepository articleMediaRepository = mock(ArticleMediaRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ArticleMediaStorageService articleMediaStorageService = mock(ArticleMediaStorageService.class);
    private final S3Client s3Client = mock(S3Client.class);
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");
        s3Properties.setPublicEndpoint("https://s3.example.com");
        articleService = new ArticleService(
                articleRepository,
                articleMediaRepository,
                categoryRepository,
                new HtmlSanitizerService(),
                articleMediaStorageService,
                s3Client,
                s3Properties
        );
    }

    @Test
    void updateArticlePersistsTheRequestedPublicationStatus() {
        UUID articleId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setStatus(ArticleStatus.WRITING);

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Vie associative");
        category.setSlug("vie-associative");

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleDto response = articleService.updateArticle(
                articleId,
                new ArticleUpdateRequest(
                        "  Programme  ",
                        "<p>Hello</p><script>alert(1)</script>",
                        null,
                        categoryId,
                        ArticleStatus.PUBLISHED
                )
        );

        assertThat(response.title()).isEqualTo("Programme");
        assertThat(response.contentHtml()).isEqualTo("<p>Hello</p>");
        assertThat(response.status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(categoryId);
    }

    @Test
    void listArticlesReturnsAdminSummariesWithCategory() {
        UUID articleId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setTitle("Programme de la semaine");
        article.setStatus(ArticleStatus.PUBLISHED);

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Vie associative");
        category.setSlug("vie-associative");
        article.setCategory(category);

        when(articleRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc()).thenReturn(List.of(article));

        List<ArticleSummaryDto> response = articleService.listArticles();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(articleId);
        assertThat(response.getFirst().title()).isEqualTo("Programme de la semaine");
        assertThat(response.getFirst().status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.getFirst().category()).isNotNull();
        assertThat(response.getFirst().category().id()).isEqualTo(categoryId);
        assertThat(response.getFirst().category().name()).isEqualTo("Vie associative");
        assertThat(response.getFirst().category().slug()).isEqualTo("vie-associative");
    }

    @Test
    void getArticleReturnsFullArticleDraftForEditing() {
        UUID articleId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setTitle("Programme de la semaine");
        article.setContentHtml("<h2>Programme</h2><p>Hello</p>");
        article.setStatus(ArticleStatus.WRITING);

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Vie associative");
        category.setSlug("vie-associative");
        article.setCategory(category);

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        ArticleDto response = articleService.getArticle(articleId);

        assertThat(response.id()).isEqualTo(articleId);
        assertThat(response.title()).isEqualTo("Programme de la semaine");
        assertThat(response.contentHtml()).isEqualTo("<h2>Programme</h2><p>Hello</p>");
        assertThat(response.coverImageUrl()).isNull();
        assertThat(response.status()).isEqualTo(ArticleStatus.WRITING);
        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(categoryId);
        assertThat(response.category().name()).isEqualTo("Vie associative");
        assertThat(response.category().slug()).isEqualTo("vie-associative");
    }

    @Test
    void listPublishedArticlesOnlyUsesPublishedRepositoryQuery() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setTitle("Programme publié");
        article.setStatus(ArticleStatus.PUBLISHED);

        when(articleRepository.findByStatusOrderByUpdatedAtDescCreatedAtDesc(ArticleStatus.PUBLISHED))
                .thenReturn(List.of(article));

        List<ArticleSummaryDto> response = articleService.listPublishedArticles();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(articleId);
        assertThat(response.getFirst().status()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    void getPublishedArticleRejectsDrafts() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.findByIdAndStatus(articleId, ArticleStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getPublishedArticle(articleId))
                .isInstanceOf(fr.insastronaute.webapi.common.web.NotFoundException.class)
                .hasMessage("Article introuvable.");
    }

    @Test
    void deleteArticleDeletesDatabaseRowAndArticleMediaPrefix() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        articleService.deleteArticle(articleId);

        verify(articleRepository).delete(article);
        verify(articleMediaStorageService).deleteArticleDirectoryQuietly(articleId);
    }

    @Test
    void uploadMediaStoresDetectedTypeAndUniqueObjectKey() {
        UUID articleId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleMediaRepository.save(any(ArticleMedia.class))).thenAnswer(invocation -> {
            ArticleMedia media = invocation.getArgument(0);
            media.setId(mediaId);
            return media;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.png",
                "application/octet-stream",
                pngBytes()
        );

        ArticleMediaUploadResponse response = articleService.uploadMedia(articleId, file);

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.url()).contains("/insastronaute-public/articles/" + articleId + "/");
        assertThat(response.url()).endsWith("-banner.png");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(putCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(putCaptor.getValue().key()).startsWith("articles/" + articleId + "/");
        assertThat(putCaptor.getValue().key()).endsWith("-banner.png");
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void uploadCoverStoresImageAndDeletesPreviousManagedCover() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setCoverImageUrl("https://s3.example.com/insastronaute-public/articles/" + articleId + "/cover/old.png");
        article.setCoverImageObjectKey("articles/" + articleId + "/cover/old.png");

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "application/octet-stream",
                pngBytes()
        );

        ArticleDto response = articleService.uploadCover(articleId, file);

        assertThat(response.coverImageUrl()).contains("/insastronaute-public/articles/" + articleId + "/cover/");
        assertThat(article.getCoverImageObjectKey()).startsWith("articles/" + articleId + "/cover/");
        assertThat(article.getCoverImageObjectKey()).endsWith("-cover.png");
        verify(articleMediaStorageService).deleteObjectQuietly("articles/" + articleId + "/cover/old.png");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(putCaptor.getValue().key()).startsWith("articles/" + articleId + "/cover/");
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void updateArticleCanSwitchCoverToExternalUrlAndDeletesPreviousManagedCover() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        article.setCoverImageUrl("https://s3.example.com/insastronaute-public/articles/" + articleId + "/cover/old.png");
        article.setCoverImageObjectKey("articles/" + articleId + "/cover/old.png");

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleDto response = articleService.updateArticle(
                articleId,
                new ArticleUpdateRequest(
                        "Programme",
                        "<p>Hello</p>",
                        " https://cdn.example.com/cover.jpg ",
                        null,
                        ArticleStatus.WRITING
                )
        );

        assertThat(response.coverImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
        assertThat(article.getCoverImageObjectKey()).isNull();
        verify(articleMediaStorageService).deleteObjectQuietly("articles/" + articleId + "/cover/old.png");
    }

    @Test
    void updateArticleKeepsManagedCoverWhenUrlIsUnchanged() {
        UUID articleId = UUID.randomUUID();
        String coverUrl = "https://s3.example.com/insastronaute-public/articles/" + articleId + "/cover/current.png";
        Article article = new Article();
        article.setId(articleId);
        article.setCoverImageUrl(coverUrl);
        article.setCoverImageObjectKey("articles/" + articleId + "/cover/current.png");

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleDto response = articleService.updateArticle(
                articleId,
                new ArticleUpdateRequest("Programme", "<p>Hello</p>", coverUrl, null, ArticleStatus.WRITING)
        );

        assertThat(response.coverImageUrl()).isEqualTo(coverUrl);
        assertThat(article.getCoverImageObjectKey()).isEqualTo("articles/" + articleId + "/cover/current.png");
        verify(articleMediaStorageService, never()).deleteObjectQuietly(any());
    }

    @Test
    void updateArticleRejectsInvalidCoverUrl() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> articleService.updateArticle(
                articleId,
                new ArticleUpdateRequest("Programme", "<p>Hello</p>", "ftp://cdn.example.com/cover.jpg", null, ArticleStatus.WRITING)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'URL de couverture doit être une URL HTTP ou HTTPS.");
    }

    @Test
    void uploadMediaRejectsExtensionMismatch() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.jpg",
                "image/jpeg",
                pngBytes()
        );

        assertThatThrownBy(() -> articleService.uploadMedia(articleId, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'extension du fichier média ne correspond pas à son format.");
    }

    @Test
    void uploadMediaRejectsTooLargeFile() {
        UUID articleId = UUID.randomUUID();
        Article article = new Article();
        article.setId(articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "video.mp4",
                "video/mp4",
                new byte[(15 * 1024 * 1024) + 1]
        );

        assertThatThrownBy(() -> articleService.uploadMedia(articleId, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le fichier média dépasse la taille maximale de 15 Mo.");
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
                0, 0, 0, 13, 'I', 'H', 'D', 'R',
                0, 0, 0, 1, 0, 0, 0, 1,
                8, 6, 0, 0, 0, 31, 21, (byte) 196, (byte) 137,
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 174, 'B', 96, (byte) 130
        };
    }
}

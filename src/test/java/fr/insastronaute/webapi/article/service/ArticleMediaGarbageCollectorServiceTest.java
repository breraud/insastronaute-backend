package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.article.repository.ArticleMediaRepository;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleMediaGarbageCollectorServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final ArticleMediaRepository articleMediaRepository = mock(ArticleMediaRepository.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private ArticleMediaGarbageCollectorService service;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");
        service = new ArticleMediaGarbageCollectorService(
                s3Client, s3Properties, articleMediaRepository, articleRepository);
    }

    @Test
    void deleteOrphanedArticleMediaRemovesObjectsAbsentFromDatabase() {
        when(articleMediaRepository.findAllObjectKeys()).thenReturn(List.of("articles/article-1/kept.jpg"));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("articles/article-1/kept.jpg").build(),
                        S3Object.builder().key("articles/article-1/orphan.jpg").build()
                )
                .build());

        service.deleteOrphanedArticleMedia();

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(listCaptor.getValue().prefix()).isEqualTo("articles/");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(deleteCaptor.getValue().delete().objects())
                .singleElement()
                .satisfies(object -> assertThat(object.key()).isEqualTo("articles/article-1/orphan.jpg"));
    }

    /**
     * La couverture d'un article n'est pas une ligne article_media : sa cle vit
     * dans une colonne de l'article. Le collecteur balaie pourtant tout le
     * prefixe articles/, couvertures comprises. Sans ce test, chaque couverture
     * televersee disparaissait a la premiere passe nocturne, et l'article
     * gardait une URL qui ne pointait plus sur rien.
     */
    @Test
    void deleteOrphanedArticleMediaKeepsArticleCoverImages() {
        when(articleMediaRepository.findAllObjectKeys()).thenReturn(List.of("articles/article-1/kept.jpg"));
        when(articleRepository.findAllCoverImageObjectKeys())
                .thenReturn(List.of("articles/article-1/cover/uuid-carte1.png"));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("articles/article-1/kept.jpg").build(),
                        S3Object.builder().key("articles/article-1/cover/uuid-carte1.png").build(),
                        S3Object.builder().key("articles/article-1/orphan.jpg").build()
                )
                .build());

        service.deleteOrphanedArticleMedia();

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().delete().objects())
                .singleElement()
                .satisfies(object -> assertThat(object.key()).isEqualTo("articles/article-1/orphan.jpg"));
    }

    @Test
    void deleteOrphanedArticleMediaDoesNotDeleteRegisteredObjects() {
        when(articleMediaRepository.findAllObjectKeys()).thenReturn(List.of("articles/article-1/kept.jpg"));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("articles/article-1/kept.jpg").build())
                .build());

        service.deleteOrphanedArticleMedia();

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}

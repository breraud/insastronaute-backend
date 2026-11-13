package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleMediaStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private ArticleMediaStorageService service;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");
        service = new ArticleMediaStorageService(s3Client, s3Properties);
    }

    @Test
    void deleteArticleDirectoryDeletesAllObjectsUnderArticlePrefix() {
        UUID articleId = UUID.randomUUID();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("articles/" + articleId + "/image.jpg").build(),
                        S3Object.builder().key("articles/" + articleId + "/video.mp4").build()
                )
                .build());

        service.deleteArticleDirectoryQuietly(articleId);

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(listCaptor.getValue().prefix()).isEqualTo("articles/" + articleId + "/");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().delete().objects())
                .extracting(object -> object.key())
                .containsExactlyInAnyOrder(
                        "articles/" + articleId + "/image.jpg",
                        "articles/" + articleId + "/video.mp4"
                );
    }
}

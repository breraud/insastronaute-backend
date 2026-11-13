package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardImageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Properties s3Properties = s3Properties();
    private final CardImageService cardImageService = new CardImageService(s3Client, s3Properties);

    @Test
    void storesCompressedJpegImageAndThumbnail() {
        UUID cardId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "card.png",
                "image/png",
                pngBytes()
        );

        CardImageService.StoredCardImages storedImages = cardImageService.store(cardId, image);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requestCaptor.capture(), any(RequestBody.class));

        List<PutObjectRequest> requests = requestCaptor.getAllValues();
        assertThat(requests)
                .extracting(PutObjectRequest::contentType)
                .containsExactly("image/jpeg", "image/jpeg");
        assertThat(requests)
                .extracting(PutObjectRequest::key)
                .allSatisfy(key -> assertThat(key).startsWith("cards/" + cardId + "/"))
                .anySatisfy(key -> assertThat(key).endsWith("/original.jpg"))
                .anySatisfy(key -> assertThat(key).endsWith("/thumbnail.jpg"));

        assertThat(storedImages.imageObjectKey()).endsWith("/original.jpg");
        assertThat(storedImages.thumbnailObjectKey()).endsWith("/thumbnail.jpg");
        assertThat(storedImages.imageUrl()).endsWith(storedImages.imageObjectKey());
        assertThat(storedImages.thumbnailUrl()).endsWith(storedImages.thumbnailObjectKey());
        assertThat(storedImages.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void mapsS3UploadFailureToStorageUnavailableAndCleansPartialObjects() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(SdkClientException.create("Read timed out"));

        assertThatThrownBy(() -> cardImageService.store(UUID.randomUUID(), new MockMultipartFile(
                "image",
                "card.png",
                "image/png",
                pngBytes()
        )))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Le stockage des images de cartes est temporairement indisponible.");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteObjectRequest::key)
                .anySatisfy(key -> assertThat(key).endsWith("/original.jpg"))
                .anySatisfy(key -> assertThat(key).endsWith("/thumbnail.jpg"));
    }

    private S3Properties s3Properties() {
        S3Properties properties = new S3Properties();
        properties.setPublicEndpoint("https://s3.example.com");
        properties.setPublicBucket("insastronaute-public");
        return properties;
    }

    private byte[] pngBytes() {
        BufferedImage image = new BufferedImage(64, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(15, 90, 160, 255));
        graphics.fillRect(0, 0, 64, 90);
        graphics.setColor(new Color(250, 250, 250, 220));
        graphics.fillOval(12, 18, 40, 40);
        graphics.dispose();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

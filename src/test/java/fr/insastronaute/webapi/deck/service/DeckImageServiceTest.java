package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeckImageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final DeckImageService deckImageService = new DeckImageService(s3Client, s3Properties());

    @Test
    void storesDeckImageInThePublicS3BucketAndReturnsItsPublicUrl() {
        UUID deckId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "cover.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        String imageUrl = deckImageService.store(deckId, image);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("insastronaute-public");
        assertThat(request.key()).startsWith("decks/" + deckId + "/");
        assertThat(request.key()).endsWith("/cover.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(request.acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
        assertThat(imageUrl).isEqualTo("https://s3.example.com/insastronaute-public/" + request.key());
    }

    @Test
    void resolvesLegacyPublicDeckImagePathsToPublicS3Urls() {
        UUID deckId = UUID.randomUUID();

        String imageUrl = deckImageService.publicUrlForStoredValue("/public/decks/" + deckId + "/image.png");

        assertThat(imageUrl).isEqualTo("https://s3.example.com/insastronaute-public/decks/" + deckId + "/image.png");
    }

    private S3Properties s3Properties() {
        S3Properties properties = new S3Properties();
        properties.setPublicEndpoint("https://s3.example.com");
        properties.setPublicBucket("insastronaute-public");
        return properties;
    }
}

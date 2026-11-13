package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CardDatasetCropStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final CardDatasetCropStorageService storageService =
            new CardDatasetCropStorageService(s3Client, s3Properties());

    @Test
    void storesTheRawCropByteForByte() throws IOException {
        // Le prealable de tout le reste : le jour ou la normalisation d'echelle change -- et
        // elle doit changer -- il faut pouvoir tout re-deriver sans re-scanner du papier.
        // Un tenseur pretraite rendrait cela impossible.
        byte[] png = pngOf(300, 80);
        UUID cropId = UUID.randomUUID();

        CardDatasetCropStorageService.StoredCrop stored = storageService.store(
                cropId, "PL-48213-3", new MockMultipartFile("image", "strip.png", "image/png", png));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        byte[] uploaded = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
        assertThat(uploaded).isEqualTo(png);

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("insastronaute-public");
        assertThat(request.key()).isEqualTo("card-dataset/crops/PL-48213-3/" + cropId + ".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.acl()).isEqualTo(ObjectCannedACL.PRIVATE);

        assertThat(stored.objectKey()).isEqualTo(request.key());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.byteSize()).isEqualTo(png.length);
        assertThat(stored.widthPx()).isEqualTo(300);
        assertThat(stored.heightPx()).isEqualTo(80);
    }

    @Test
    void refusesWhatIsNotAnImage() {
        assertThatThrownBy(() -> storageService.store(UUID.randomUUID(), "PL-48213-3",
                new MockMultipartFile("image", "strip.pdf", "application/pdf", new byte[]{1, 2, 3})))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storageService.store(UUID.randomUUID(), "PL-48213-3",
                new MockMultipartFile("image", "strip.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verify(s3Client, org.mockito.Mockito.never())
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private byte[] pngOf(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private S3Properties s3Properties() {
        S3Properties properties = new S3Properties();
        properties.setPublicBucket("insastronaute-public");
        return properties;
    }
}

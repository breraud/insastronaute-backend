package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import fr.insastronaute.webapi.common.web.NotFoundException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardDatasetSheetStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Properties properties = properties();
    private final CardDatasetSheetStorageService storage = new CardDatasetSheetStorageService(s3Client, properties);

    @Test
    void storesTheOriginalPdfPrivatelyUnderTheDatasetSheetPrefix() throws Exception {
        UUID sheetId = UUID.randomUUID();
        byte[] original = new byte[]{'%', 'P', 'D', 'F', 1, 2, 3};

        String key = storage.store(sheetId, original);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(key).isEqualTo("card-print/dataset-sheets/" + sheetId + ".pdf");
        assertThat(request.getValue().bucket()).isEqualTo("public-test");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().acl()).isEqualTo(ObjectCannedACL.PRIVATE);
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(original);
    }

    @Test
    void readsBackTheExactOriginalBytes() {
        byte[] original = new byte[]{'%', 'P', 'D', 'F', 9, 8, 7};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), original));

        byte[] downloaded = storage.read("card-print/dataset-sheets/sheet.pdf");

        assertThat(downloaded).isEqualTo(original);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("public-test");
        assertThat(request.getValue().key()).isEqualTo("card-print/dataset-sheets/sheet.pdf");
    }

    @Test
    void deletesAnOrphanedPdfFromTheSamePrivateBucket() {
        storage.delete("card-print/dataset-sheets/orphan.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("public-test");
        assertThat(request.getValue().key()).isEqualTo("card-print/dataset-sheets/orphan.pdf");
    }

    @Test
    void hidesS3DetailsWhenTheOriginalPdfCannotBeStored() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.builder().message("secret endpoint detail").build());

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), new byte[]{'%'}))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Le stockage des planches de dataset est temporairement indisponible.")
                .hasMessageNotContaining("secret endpoint detail");
    }

    @Test
    void hidesS3DetailsWhenTheOriginalPdfCannotBeRead() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("secret endpoint detail").build());

        assertThatThrownBy(() -> storage.read("card-print/dataset-sheets/sheet.pdf"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessage("Le stockage des planches de dataset est temporairement indisponible.")
                .hasMessageNotContaining("secret endpoint detail");
    }

    @Test
    void namesTheMissingKeyInsteadOfBlamingTheStorage() {
        // Un objet absent n'est pas une panne. Confondre les deux fait chercher un incident
        // d'infra qui n'existe pas, au lieu de la vraie question : qui a efface cette cle.
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist").build());

        assertThatThrownBy(() -> storage.read("card-print/dataset-sheets/sheet.pdf"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("card-print/dataset-sheets/sheet.pdf");
    }

    private static S3Properties properties() {
        S3Properties properties = new S3Properties();
        properties.setPublicBucket("public-test");
        return properties;
    }
}

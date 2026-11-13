package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CardPrintStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Properties s3Properties = s3Properties();
    private final CardPrintStorageService storageService = new CardPrintStorageService(s3Client, s3Properties);

    @Test
    void storesGeneratedRunPdfAsPrivateObject() {
        UUID runId = UUID.randomUUID();

        CardPrintStorageService.StoredRunPdf storedRunPdf = storageService.storeRun(runId, new byte[]{'%','P','D','F'});

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("insastronaute-public");
        assertThat(request.key()).isEqualTo("card-print/runs/" + runId + ".pdf");
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.acl()).isEqualTo(ObjectCannedACL.PRIVATE);
        assertThat(storedRunPdf.objectKey()).isEqualTo(request.key());
    }

    private S3Properties s3Properties() {
        S3Properties properties = new S3Properties();
        properties.setPublicBucket("insastronaute-public");
        return properties;
    }
}

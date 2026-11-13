package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasureHuntStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Test
    void storesAnAdminRewardInThePrivateBucket() {
        TreasureHuntStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lore.pdf",
                "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII)
        );

        String storagePath = service.store(file);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("private-rewards");
        assertThat(requestCaptor.getValue().key()).startsWith("treasure-hunt/").endsWith("/lore.pdf");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(storagePath).isEqualTo(requestCaptor.getValue().key());
    }

    @Test
    void createsAFifteenMinuteGetPresignedUrlForAStoredReward() throws Exception {
        TreasureHuntStorageService service = service();
        PresignedGetObjectRequest signed = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://s3.example/reward.pdf").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        String url = service.createReadUrl("treasure-hunt/abc/reward.pdf");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().signatureDuration()).hasSeconds(900);
        assertThat(requestCaptor.getValue().getObjectRequest().bucket()).isEqualTo("private-rewards");
        assertThat(url).isEqualTo("https://s3.example/reward.pdf");
    }

    @Test
    void rejectsAnUnsupportedRewardFile() {
        TreasureHuntStorageService service = service();
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsExecutableContentDeclaredAsPdf() {
        TreasureHuntStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lore.pdf",
                "application/pdf",
                "MZ executable payload".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le contenu du fichier de récompense ne correspond pas à son type déclaré.");
    }

    private TreasureHuntStorageService service() {
        S3Properties properties = new S3Properties();
        properties.setBucket("private-rewards");
        return new TreasureHuntStorageService(s3Client, s3Presigner, properties);
    }
}

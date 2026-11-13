package fr.insastronaute.webapi.game.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.game.domain.GameAssetRelease;
import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.repository.GameAssetReleaseRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameAssetServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final GameAssetReleaseRepository releaseRepository = mock(GameAssetReleaseRepository.class);
    private GameAssetService gameAssetService;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setEndpoint("http://localhost:8333");
        s3Properties.setPublicEndpoint("https://s3.example.com");
        s3Properties.setPublicBucket("insastronaute-public");
        gameAssetService = new GameAssetService(s3Client, s3Properties, releaseRepository);
    }

    @Test
    void publishUploadsVersionedFolderAndDeletesPreviousVersion() {
        GameAssetRelease previousRelease = new GameAssetRelease();
        previousRelease.setId(GameAssetRelease.ACTIVE_RELEASE_ID);
        previousRelease.setVersion("2026.0.0");
        previousRelease.setObjectPrefix("game/2026.0.0/");
        previousRelease.setBaseUrl("https://s3.example.com/insastronaute-public/game/2026.0.0/");
        previousRelease.setFileCount(5);
        previousRelease.setTotalSizeBytes(5);

        when(releaseRepository.findById(GameAssetRelease.ACTIVE_RELEASE_ID)).thenReturn(Optional.of(previousRelease));
        when(releaseRepository.saveAndFlush(any(GameAssetRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("game/2026.0.0/index.html").build())
                .build());

        GameAssetReleaseDto response = gameAssetService.publish("2026.1.0", requiredFiles());

        assertThat(response.version()).isEqualTo("2026.1.0");
        assertThat(response.baseUrl()).isEqualTo("https://s3.example.com/insastronaute-public/game/2026.1.0/");
        assertThat(response.loaderUrl()).isEqualTo("https://s3.example.com/insastronaute-public/game/2026.1.0/Build/game.loader.js");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(6)).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.contentDisposition()).isEqualTo("attachment"));
        assertThat(putCaptor.getAllValues()).anySatisfy(request -> {
            assertThat(request.key()).isEqualTo("game/2026.1.0/Build/game.framework.js.br");
            assertThat(request.contentType()).isEqualTo("application/javascript");
            assertThat(request.contentEncoding()).isEqualTo("br");
            assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        });

        ArgumentCaptor<GameAssetRelease> releaseCaptor = ArgumentCaptor.forClass(GameAssetRelease.class);
        verify(releaseRepository).saveAndFlush(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getObjectPrefix()).isEqualTo("game/2026.1.0/");
        assertThat(releaseCaptor.getValue().getFileCount()).isEqualTo(5);
        assertThat(response.manifestUrl()).endsWith("/asset-manifest.json");
        assertThat(putCaptor.getAllValues()).anySatisfy(request -> {
            assertThat(request.key()).isEqualTo("game/2026.1.0/asset-manifest.json");
            assertThat(request.contentType()).isEqualTo("application/json");
            assertThat(request.contentEncoding()).isNull();
        });

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().prefix()).isEqualTo("game/2026.0.0/");
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void publishStoresLargeFileAsRawChunksAndBrotliFallback() {
        when(releaseRepository.findById(GameAssetRelease.ACTIVE_RELEASE_ID)).thenReturn(Optional.empty());
        when(releaseRepository.saveAndFlush(any(GameAssetRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] largeData = new byte[GameAssetChunker.CHUNK_SIZE_BYTES + 1];

        List<MultipartFile> files = requiredFiles();
        files.set(1, new MockMultipartFile(
                "files",
                "public/game/Build/game.data.br",
                "application/octet-stream",
                largeData
        ));

        GameAssetReleaseDto response = gameAssetService.publish("2026.2.0", files);

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(8)).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getAllValues()).anySatisfy(request -> {
            assertThat(request.key()).isEqualTo("game/2026.2.0/chunks/Build/game.data.br/00000.part");
            assertThat(request.contentEncoding()).isNull();
            assertThat(request.contentType()).isEqualTo("application/octet-stream");
        });
        assertThat(putCaptor.getAllValues()).anySatisfy(request -> {
            assertThat(request.key()).isEqualTo("game/2026.2.0/fallback/Build/game.data.br");
            assertThat(request.contentEncoding()).isEqualTo("br");
            assertThat(request.contentType()).isEqualTo("application/octet-stream");
        });
        assertThat(putCaptor.getAllValues())
                .noneMatch(request -> request.key().equals("game/2026.2.0/Build/game.data.br"));
        assertThat(response.fileCount()).isEqualTo(5);
        assertThat(response.totalSizeBytes()).isEqualTo(GameAssetChunker.CHUNK_SIZE_BYTES + 5L);
    }

    @Test
    void publishRejectsSameVersionAsActiveRelease() {
        GameAssetRelease previousRelease = new GameAssetRelease();
        previousRelease.setVersion("2026.1.0");
        previousRelease.setObjectPrefix("game/2026.1.0/");

        when(releaseRepository.findById(GameAssetRelease.ACTIVE_RELEASE_ID)).thenReturn(Optional.of(previousRelease));

        assertThatThrownBy(() -> gameAssetService.publish("2026.1.0", requiredFiles()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("version de jeu doit changer");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void publishRejectsFolderWithoutRequiredUnityFiles() {
        assertThatThrownBy(() -> gameAssetService.publish("2026.1.0", List.of(gameFile("public/game/index.html"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("fichiers Unity requis");
    }

    @Test
    void publishRejectsPathTraversal() {
        assertThatThrownBy(() -> gameAssetService.publish("2026.1.0", List.of(gameFile("../index.html"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sort du dossier autorisé");
    }

    private List<MultipartFile> requiredFiles() {
        return new java.util.ArrayList<>(List.of(
                gameFile("public/game/index.html"),
                gameFile("public/game/Build/game.data.br"),
                gameFile("public/game/Build/game.framework.js.br"),
                gameFile("public/game/Build/game.loader.js"),
                gameFile("public/game/Build/game.wasm.br")
        ));
    }

    private MockMultipartFile gameFile(String path) {
        return new MockMultipartFile(
                "files",
                path,
                "application/octet-stream",
                new byte[]{1}
        );
    }
}

package fr.insastronaute.webapi.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.game.config.GameConfigProperties;
import fr.insastronaute.webapi.game.dto.GameConfigImportDto;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameConfigServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private GameConfigService gameConfigService;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");

        GameConfigProperties gameConfigProperties = new GameConfigProperties();
        gameConfigProperties.setObjectPrefix("GameData/current/dev");
        gameConfigProperties.setReleasesObjectPrefix("GameData/versions");

        gameConfigService = new GameConfigService(
                s3Client,
                s3Properties,
                new ObjectMapper().findAndRegisterModules(),
                gameConfigProperties
        );
    }

    // ── Aller-retour de la configuration ────────────────────────────────────────

    private static MultipartFile zip(Map<String, String> files, String... folders) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (String folder : folders) {
                archive.putNextEntry(new ZipEntry(folder + "/"));
                archive.closeEntry();
            }
            for (Map.Entry<String, String> file : files.entrySet()) {
                archive.putNextEntry(new ZipEntry(file.getKey()));
                archive.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return new MockMultipartFile("archive", "configs.zip", "application/zip", buffer.toByteArray());
    }

    private static Map<String, String> entriesOf(byte[] archive) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    /** Les dossiers vides survivent : l'editeur les affiche, l'export doit les rendre. */
    @Test
    void exportCarriesEveryFileAndTheEmptyFoldersToo() throws IOException {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("GameData/current/dev/cards.json").build(),
                        S3Object.builder().key("GameData/current/dev/vide/").build()
                )
                .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8)));

        Map<String, String> entries = entriesOf(gameConfigService.exportArchive());

        assertThat(entries).containsKeys("cards.json", "vide/");
        assertThat(entries.get("cards.json")).isEqualTo("{\"a\":1}");
    }

    @Test
    void exportRefusesWhenThereIsNothingToExport() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());

        assertThatThrownBy(() -> gameConfigService.exportArchive())
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void importWritesEveryFileOfTheArchive() throws IOException {
        MultipartFile archive = zip(Map.of("cards.json", "{\"a\":1}", "nested/zones.json", "[]"));

        GameConfigImportDto result = gameConfigService.importArchive(archive, false);

        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(result.replaced()).isFalse();
        assertThat(result.deletedCount()).isZero();
        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(puts.capture(), any(RequestBody.class));
        assertThat(puts.getAllValues()).extracting(PutObjectRequest::key)
                .containsExactlyInAnyOrder("GameData/current/dev/cards.json", "GameData/current/dev/nested/zones.json");
        // Sans remplacement, rien n'est efface : les fichiers absents de l'archive restent.
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    /**
     * Un JSON invalide au milieu de l'archive n'ecrit rien du tout.
     *
     * <p>C'est le coeur du contrat : une configuration a moitie importee -- quelques
     * fichiers du nouvel etat, le reste de l'ancien -- ne correspond a aucune version
     * connue, et rien n'indiquerait ou la coupure a eu lieu.</p>
     */
    @Test
    void aSingleBadFileAbortsTheWholeImport() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("bon.json", "{\"a\":1}");
        files.put("casse.json", "{ pas du json");

        assertThatThrownBy(() -> gameConfigService.importArchive(zip(files), false))
                .isInstanceOf(BadRequestException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    /** Meme en mode remplacement : on n'efface pas avant d'avoir tout valide. */
    @Test
    void aBadArchiveNeverWipesTheExistingConfiguration() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("bon.json", "{}");
        files.put("../evasion.json", "{}");

        assertThatThrownBy(() -> gameConfigService.importArchive(zip(files), true))
                .isInstanceOf(BadRequestException.class);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void replacingWipesTheEditablePrefixFirst() throws IOException {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("GameData/current/dev/ancien.json").build())
                .build());

        GameConfigImportDto result = gameConfigService.importArchive(zip(Map.of("cards.json", "{}")), true);

        assertThat(result.replaced()).isTrue();
        assertThat(result.deletedCount()).isEqualTo(1);
        ArgumentCaptor<DeleteObjectsRequest> deletes = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deletes.capture());
        assertThat(deletes.getValue().delete().objects()).extracting(ObjectIdentifier::key)
                .containsExactly("GameData/current/dev/ancien.json");
    }

    @Test
    void importRefusesAnArchiveWithoutAnyConfiguration() throws IOException {
        assertThatThrownBy(() -> gameConfigService.importArchive(zip(Map.of()), false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listReturnsOnlyJsonFilesUnderConfiguredPrefix() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("GameData/current/dev/zones.json").build(),
                        S3Object.builder().key("GameData/current/dev/readme.txt").build(),
                        S3Object.builder().key("GameData/current/dev/nested/ignored.json").build(),
                        S3Object.builder().key("GameData/current/dev/cards.json").build()
                )
                .build());

        List<String> result = gameConfigService.list();

        assertThat(result).containsExactly("cards.json", "nested/ignored.json", "zones.json");
        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(requestCaptor.getValue().prefix()).isEqualTo("GameData/current/dev/");
    }

    @Test
    void listFoldersReturnsFolderMarkersAndParentsFromNestedFiles() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("GameData/current/dev/levels/").build(),
                        S3Object.builder().key("GameData/current/dev/levels/world_1/settings.json").build(),
                        S3Object.builder().key("GameData/current/dev/cards.json").build()
                )
                .build());

        List<String> result = gameConfigService.listFolders();

        assertThat(result).containsExactly("levels", "levels/world_1");
    }

    @Test
    void readReturnsObjectText() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                "{\"enabled\":true}".getBytes(StandardCharsets.UTF_8)
        ));

        String result = gameConfigService.read("settings.json");

        assertThat(result).isEqualTo("{\"enabled\":true}");
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/current/dev/settings.json");
    }

    @Test
    void readMapsMissingS3ObjectToNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .message("not found")
                .build());

        assertThatThrownBy(() -> gameConfigService.read("settings.json"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void writeValidatesJsonAndUploadsToConfiguredPrefix() {
        gameConfigService.write("settings.json", "{\"enabled\":true}");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/current/dev/settings.json");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/json; charset=utf-8");
    }

    @Test
    void writeSupportsJsonFilesInSubfolders() {
        gameConfigService.write("levels/world_1/settings.json", "{\"enabled\":true}");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/current/dev/levels/world_1/settings.json");
    }

    @Test
    void createFolderCreatesS3FolderMarkerUnderConfiguredPrefix() {
        gameConfigService.createFolder("levels/world_1");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/current/dev/levels/world_1/");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/x-directory");
    }

    @Test
    void createFolderRejectsUnsafePath() {
        assertThatThrownBy(() -> gameConfigService.createFolder("../levels"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dossier");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deleteFileDeletesJsonObjectUnderConfiguredPrefix() {
        gameConfigService.deleteFile("levels/world_1/settings.json");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/current/dev/levels/world_1/settings.json");
    }

    @Test
    void deleteFileRejectsUnsafePath() {
        assertThatThrownBy(() -> gameConfigService.deleteFile("../settings.json"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chemin de fichier");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFolderDeletesFolderPrefixRecursively() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("GameData/current/dev/levels/world_1/").build(),
                        S3Object.builder().key("GameData/current/dev/levels/world_1/settings.json").build()
                )
                .build());

        gameConfigService.deleteFolder("levels/world_1");

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().prefix()).isEqualTo("GameData/current/dev/levels/world_1/");

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().delete().objects()).extracting(ObjectIdentifier::key).containsExactly(
                "GameData/current/dev/levels/world_1/",
                "GameData/current/dev/levels/world_1/settings.json"
        );
    }

    @Test
    void deleteFolderRejectsMissingFolder() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());

        assertThatThrownBy(() -> gameConfigService.deleteFolder("levels/world_1"))
                .isInstanceOf(NotFoundException.class);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void writeRejectsInvalidJson() {
        assertThatThrownBy(() -> gameConfigService.write("settings.json", "{invalid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JSON");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void writeRejectsUnsafeFileName() {
        assertThatThrownBy(() -> gameConfigService.write("../settings.json", "{}"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chemin de fichier");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void releaseCopiesCurrentJsonFilesToVersionedPrefixAndUpdatesLatestMetadata() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("GameData/current/dev/cards.json").build(),
                        S3Object.builder().key("GameData/current/dev/settings.json").build()
                )
                .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            String content = request.key().endsWith("cards.json") ? "{\"cards\":[]}" : "{\"enabled\":true}";
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().build(),
                    content.getBytes(StandardCharsets.UTF_8)
            );
        });

        GameConfigReleaseDto release = gameConfigService.release("2026.1.0");

        assertThat(release.version()).isEqualTo("2026.1.0");
        assertThat(release.objectPrefix()).isEqualTo("GameData/versions/2026.1.0/");
        assertThat(release.files()).containsExactly("cards.json", "settings.json");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(3)).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getAllValues()).extracting(PutObjectRequest::key).containsExactly(
                "GameData/versions/2026.1.0/cards.json",
                "GameData/versions/2026.1.0/settings.json",
                "GameData/versions/latest.json"
        );
        assertThat(requestCaptor.getAllValues().getFirst().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(requestCaptor.getAllValues().getLast().cacheControl()).isEqualTo("no-cache");
    }

    @Test
    void releaseRejectsUnsafeVersion() {
        assertThatThrownBy(() -> gameConfigService.release("../2026"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("version");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void releaseUploadedPublishesMultipartJsonTreeAndUpdatesLatestMetadata() {
        GameConfigReleaseDto release = gameConfigService.releaseUploaded("2026.1.0", List.of(
                configFile("cards.json", "{\"cards\":[]}"),
                configFile("levels/world_1/settings.json", "{\"enabled\":true}")
        ));

        assertThat(release.version()).isEqualTo("2026.1.0");
        assertThat(release.objectPrefix()).isEqualTo("GameData/versions/2026.1.0/");
        assertThat(release.files()).containsExactly("cards.json", "levels/world_1/settings.json");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(3)).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getAllValues()).extracting(PutObjectRequest::key).containsExactly(
                "GameData/versions/2026.1.0/cards.json",
                "GameData/versions/2026.1.0/levels/world_1/settings.json",
                "GameData/versions/latest.json"
        );
    }

    @Test
    void releaseUploadedRejectsInvalidJsonFile() {
        assertThatThrownBy(() -> gameConfigService.releaseUploaded("2026.1.0", List.of(
                configFile("settings.json", "{invalid")
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JSON");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void releaseUploadedRejectsDuplicatePaths() {
        assertThatThrownBy(() -> gameConfigService.releaseUploaded("2026.1.0", List.of(
                configFile("settings.json", "{}"),
                configFile("settings.json", "{}")
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("même chemin");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void releaseUploadedRejectsNonJsonPaths() {
        assertThatThrownBy(() -> gameConfigService.releaseUploaded("2026.1.0", List.of(
                configFile("readme.txt", "{}")
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(".json");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void listReleasesReturnsExistingVersionedConfigsNewestFirst() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder()
                                .key("GameData/versions/latest.json")
                                .lastModified(Instant.parse("2026-07-09T12:00:00Z"))
                                .build(),
                        S3Object.builder()
                                .key("GameData/versions/2026.1.0/settings.json")
                                .lastModified(Instant.parse("2026-07-09T10:00:00Z"))
                                .build(),
                        S3Object.builder()
                                .key("GameData/versions/2026.0.0/cards.json")
                                .lastModified(Instant.parse("2026-07-08T10:00:00Z"))
                                .build(),
                        S3Object.builder()
                                .key("GameData/versions/2026.0.0/readme.txt")
                                .lastModified(Instant.parse("2026-07-08T10:00:00Z"))
                                .build()
                )
                .build());

        List<GameConfigReleaseDto> releases = gameConfigService.listReleases();

        assertThat(releases).extracting(GameConfigReleaseDto::version)
                .containsExactly("2026.1.0", "2026.0.0");
        assertThat(releases.getFirst().files()).containsExactly("settings.json");
        assertThat(releases.getLast().files()).containsExactly("cards.json");

        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(requestCaptor.capture());
        assertThat(requestCaptor.getValue().prefix()).isEqualTo("GameData/versions/");
    }

    @Test
    void readReleaseFileReturnsObjectText() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                "{\"enabled\":true}".getBytes(StandardCharsets.UTF_8)
        ));

        String result = gameConfigService.readReleaseFile("2026.1.0", "levels/world_1/settings.json");

        assertThat(result).isEqualTo("{\"enabled\":true}");
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/versions/2026.1.0/levels/world_1/settings.json");
    }

    @Test
    void latestReleaseReadsReleaseMetadata() throws Exception {
        String latest = new ObjectMapper().findAndRegisterModules().writeValueAsString(new GameConfigReleaseDto(
                "2026.1.0",
                "GameData/versions/2026.1.0/",
                List.of("settings.json"),
                Instant.parse("2026-07-09T10:00:00Z")
        ));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                latest.getBytes(StandardCharsets.UTF_8)
        ));

        GameConfigReleaseDto release = gameConfigService.latestRelease();

        assertThat(release.version()).isEqualTo("2026.1.0");
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertThat(requestCaptor.getValue().key()).isEqualTo("GameData/versions/latest.json");
    }

    @Test
    void latestReleaseMapsMissingMetadataToNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .message("not found")
                .build());

        assertThatThrownBy(() -> gameConfigService.latestRelease())
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteReleaseDeletesVersionPrefixAndLatestMetadataWhenVersionIsLatest() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            if ("GameData/versions/2026.1.0/".equals(request.prefix())) {
                return ListObjectsV2Response.builder()
                        .contents(
                                S3Object.builder().key("GameData/versions/2026.1.0/cards.json").build(),
                                S3Object.builder().key("GameData/versions/2026.1.0/settings.json").build()
                        )
                        .build();
            }
            return ListObjectsV2Response.builder().build();
        });
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                latestReleaseJson("2026.1.0").getBytes(StandardCharsets.UTF_8)
        ));

        gameConfigService.deleteRelease("2026.1.0");

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getAllValues()).extracting(ListObjectsV2Request::prefix).containsExactly(
                "GameData/versions/2026.1.0/",
                "GameData/versions/"
        );

        ArgumentCaptor<DeleteObjectsRequest> deleteObjectsCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteObjectsCaptor.capture());
        assertThat(deleteObjectsCaptor.getValue().delete().objects()).extracting(ObjectIdentifier::key).containsExactly(
                "GameData/versions/2026.1.0/cards.json",
                "GameData/versions/2026.1.0/settings.json"
        );

        ArgumentCaptor<DeleteObjectRequest> deleteObjectCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteObjectCaptor.capture());
        assertThat(deleteObjectCaptor.getValue().key()).isEqualTo("GameData/versions/latest.json");
    }

    @Test
    void deleteReleasePromotesPreviousReleaseWhenDeletingLatest() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            if ("GameData/versions/2026.1.0/".equals(request.prefix())) {
                return ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("GameData/versions/2026.1.0/settings.json").build())
                        .build();
            }
            return ListObjectsV2Response.builder()
                    .contents(
                            S3Object.builder()
                                    .key("GameData/versions/2026.0.0/cards.json")
                                    .lastModified(Instant.parse("2026-07-08T10:00:00Z"))
                                    .build(),
                            S3Object.builder()
                                    .key("GameData/versions/2026.0.0/settings.json")
                                    .lastModified(Instant.parse("2026-07-08T10:05:00Z"))
                                    .build()
                    )
                    .build();
        });
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                latestReleaseJson("2026.1.0").getBytes(StandardCharsets.UTF_8)
        ));

        gameConfigService.deleteRelease("2026.1.0");

        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().key()).isEqualTo("GameData/versions/latest.json");
    }

    @Test
    void deleteReleaseKeepsLatestMetadataWhenDeletingOlderVersion() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("GameData/versions/2026.0.0/settings.json").build())
                .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                latestReleaseJson("2026.1.0").getBytes(StandardCharsets.UTF_8)
        ));

        gameConfigService.deleteRelease("2026.0.0");

        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteReleaseRejectsMissingVersionPrefix() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());

        assertThatThrownBy(() -> gameConfigService.deleteRelease("2026.1.0"))
                .isInstanceOf(NotFoundException.class);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    private String latestReleaseJson(String version) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(new GameConfigReleaseDto(
                version,
                "GameData/versions/" + version + "/",
                List.of("settings.json"),
                Instant.parse("2026-07-09T10:00:00Z")
        ));
    }

    private MultipartFile configFile(String path, String content) {
        return new MockMultipartFile(
                "files",
                path,
                "application/json",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}

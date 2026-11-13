package fr.insastronaute.webapi.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.game.domain.GameAssetRelease;
import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.dto.GameAssetManifest;
import fr.insastronaute.webapi.game.repository.GameAssetReleaseRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GameAssetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameAssetService.class);
    private static final String OBJECT_ROOT = "game";
    private static final String VERSION_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$";
    private static final long MAX_TOTAL_SIZE_BYTES = 512L * 1024L * 1024L;
    private static final int MANIFEST_FORMAT_VERSION = 2;
    private static final Set<String> REQUIRED_FILES = Set.of(
            "index.html",
            "Build/game.data.br",
            "Build/game.framework.js.br",
            "Build/game.loader.js",
            "Build/game.wasm.br"
    );

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final GameAssetReleaseRepository releaseRepository;
    private final GameAssetChunker chunker = new GameAssetChunker();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameAssetService(
            S3Client s3Client,
            S3Properties s3Properties,
            GameAssetReleaseRepository releaseRepository
    ) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.releaseRepository = releaseRepository;
    }

    @Transactional(readOnly = true)
    public GameAssetReleaseDto getActiveRelease() {
        return releaseRepository.findById(GameAssetRelease.ACTIVE_RELEASE_ID)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Aucune version de jeu n'est publiée."));
    }

    @Transactional
    public GameAssetReleaseDto publish(String version, List<MultipartFile> files) {
        String normalizedVersion = validateVersion(version);
        List<UploadFile> uploadFiles = validateFiles(files);
        GameAssetRelease previousRelease = releaseRepository.findById(GameAssetRelease.ACTIVE_RELEASE_ID).orElse(null);
        if (previousRelease != null && previousRelease.getVersion().equals(normalizedVersion)) {
            throw new BadRequestException("La version de jeu doit changer pour invalider le cache des clients.");
        }
        String previousObjectPrefix = previousRelease == null ? null : previousRelease.getObjectPrefix();

        String objectPrefix = OBJECT_ROOT + "/" + normalizedVersion + "/";
        GameAssetRelease savedRelease;
        try {
            long totalSizeBytes = publishFiles(objectPrefix, uploadFiles);

            GameAssetRelease release = previousRelease == null ? new GameAssetRelease() : previousRelease;
            release.setId(GameAssetRelease.ACTIVE_RELEASE_ID);
            release.setVersion(normalizedVersion);
            release.setObjectPrefix(objectPrefix);
            release.setBaseUrl(publicUrl(objectPrefix));
            release.setFileCount(uploadFiles.size());
            release.setTotalSizeBytes(totalSizeBytes);
            release.setPublishedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
            savedRelease = releaseRepository.saveAndFlush(release);
        } catch (RuntimeException exception) {
            deletePrefixQuietly(objectPrefix);
            throw exception;
        }

        if (previousObjectPrefix != null && !previousObjectPrefix.equals(objectPrefix)) {
            deletePrefixQuietly(previousObjectPrefix);
        }

        return toDto(savedRelease);
    }

    private long publishFiles(String objectPrefix, List<UploadFile> uploadFiles) {
        long totalSizeBytes = 0L;
        Map<String, GameAssetManifest.Asset> manifestAssets = new LinkedHashMap<>();

        for (UploadFile uploadFile : uploadFiles) {
            byte[] bytes = readBytes(uploadFile.file());
            totalSizeBytes += bytes.length;
            if (!chunker.shouldChunk(bytes.length)) {
                putObject(objectPrefix + uploadFile.relativePath(), bytes, uploadFile.file().getContentType(), true);
                continue;
            }

            GameAssetChunker.ChunkedAsset chunkedAsset = chunker.chunk(uploadFile.relativePath(), bytes);
            List<GameAssetManifest.Chunk> manifestChunks = new ArrayList<>();
            for (GameAssetChunker.Chunk chunk : chunkedAsset.chunks()) {
                String chunkPath = "chunks/" + uploadFile.relativePath() + "/%05d.part".formatted(chunk.index());
                putObject(objectPrefix + chunkPath, chunk.bytes(), "application/octet-stream", false);
                manifestChunks.add(new GameAssetManifest.Chunk(chunkPath, chunk.bytes().length, chunk.sha256()));
            }

            String fallbackPath = "fallback/" + uploadFile.relativePath();
            putObject(objectPrefix + fallbackPath, bytes, uploadFile.file().getContentType(), true);
            manifestAssets.put(uploadFile.relativePath(), new GameAssetManifest.Asset(
                    uploadFile.relativePath().toLowerCase(Locale.ROOT).endsWith(".br") ? "br" : "identity",
                    chunkedAsset.sizeBytes(),
                    chunkedAsset.sha256(),
                    fallbackPath,
                    List.copyOf(manifestChunks)
            ));
        }

        GameAssetManifest manifest = new GameAssetManifest(
                MANIFEST_FORMAT_VERSION,
                GameAssetChunker.CHUNK_SIZE_BYTES,
                Map.copyOf(manifestAssets)
        );
        putObject(objectPrefix + "asset-manifest.json", serializeManifest(manifest), "application/json", false);
        return totalSizeBytes;
    }

    private String validateVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new BadRequestException("La version de jeu est requise.");
        }
        String normalizedVersion = version.trim();
        if (!normalizedVersion.matches(VERSION_PATTERN)) {
            throw new BadRequestException("La version de jeu doit contenir uniquement lettres, chiffres, points, tirets ou underscores.");
        }
        return normalizedVersion;
    }

    private List<UploadFile> validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Le dossier de jeu est requis.");
        }

        List<UploadFile> uploadFiles = new ArrayList<>();
        Set<String> relativePaths = new HashSet<>();
        long totalSizeBytes = 0L;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String relativePath = normalizeRelativePath(file.getOriginalFilename());
            if (!relativePaths.add(relativePath)) {
                throw new BadRequestException("Le dossier de jeu contient deux fichiers avec le même chemin: " + relativePath);
            }

            totalSizeBytes += file.getSize();
            if (totalSizeBytes > MAX_TOTAL_SIZE_BYTES) {
                throw new BadRequestException("Le dossier de jeu ne doit pas dépasser 512 Mo.");
            }

            uploadFiles.add(new UploadFile(relativePath, file));
        }

        if (uploadFiles.isEmpty()) {
            throw new BadRequestException("Le dossier de jeu ne contient aucun fichier.");
        }

        if (!relativePaths.containsAll(REQUIRED_FILES)) {
            throw new BadRequestException("Le dossier de jeu doit contenir index.html et les fichiers Unity requis dans Build/.");
        }

        return uploadFiles;
    }

    private String normalizeRelativePath(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Chaque fichier de jeu doit avoir un chemin relatif.");
        }

        String rawPath = originalFilename.replace('\\', '/');
        if (rawPath.contains("\0")) {
            throw new BadRequestException("Le chemin d'un fichier de jeu est invalide.");
        }

        while (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }

        rawPath = stripPrefix(rawPath, "public/game/");
        rawPath = stripPrefix(rawPath, "game/");
        Path normalizedPath = Path.of(rawPath).normalize();
        String relativePath = normalizedPath.toString().replace('\\', '/');
        if (relativePath.isBlank() || relativePath.startsWith("../") || relativePath.equals("..") || relativePath.startsWith("/")) {
            throw new BadRequestException("Le chemin d'un fichier de jeu sort du dossier autorisé.");
        }
        return relativePath;
    }

    private String stripPrefix(String path, String prefix) {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire un fichier du dossier de jeu.");
        }
    }

    private byte[] serializeManifest(GameAssetManifest manifest) {
        try {
            return objectMapper.writeValueAsBytes(manifest);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser le manifeste du jeu.", exception);
        }
    }

    private void putObject(
            String objectKey,
            byte[] bytes,
            String declaredContentType,
            boolean applyContentEncoding
    ) {
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .contentType(contentType(objectKey, declaredContentType))
                    .contentDisposition("attachment")
                    .cacheControl("public, max-age=31536000, immutable")
                    .acl(ObjectCannedACL.PUBLIC_READ);
            String contentEncoding = applyContentEncoding ? contentEncoding(objectKey) : null;
            if (contentEncoding != null) {
                request.contentEncoding(contentEncoding);
            }

            s3Client.putObject(request.build(),
                    RequestBody.fromBytes(bytes));
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private String contentType(String objectKey, String declaredContentType) {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lowerKey.endsWith(".js") || lowerKey.endsWith(".js.br")) {
            return "application/javascript";
        }
        if (lowerKey.endsWith(".wasm") || lowerKey.endsWith(".wasm.br")) {
            return "application/wasm";
        }
        if (lowerKey.endsWith(".json")) {
            return "application/json";
        }
        if (lowerKey.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lowerKey.endsWith(".png")) {
            return "image/png";
        }
        if (lowerKey.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lowerKey.endsWith(".webmanifest")) {
            return "application/manifest+json";
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            return declaredContentType;
        }
        return "application/octet-stream";
    }

    private String contentEncoding(String objectKey) {
        return objectKey.toLowerCase(Locale.ROOT).endsWith(".br") ? "br" : null;
    }

    private String publicUrl(String objectPrefix) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier le jeu.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectPrefix).toString();
    }

    private void deletePrefix(String objectPrefix) {
        String continuationToken = null;
        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(objectPrefix)
                    .continuationToken(continuationToken)
                    .build());
            deleteObjects(response.contents());
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
    }

    private void deleteObjects(List<S3Object> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> identifiers = objects.stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();

        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .delete(Delete.builder().objects(identifiers).build())
                .build());
    }

    private void deletePrefixQuietly(String objectPrefix) {
        try {
            deletePrefix(objectPrefix);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not delete previous game asset prefix '{}' from public S3 bucket '{}'.",
                    objectPrefix,
                    s3Properties.getPublicBucket(),
                    exception
            );
        }
    }

    private GameAssetReleaseDto toDto(GameAssetRelease release) {
        return new GameAssetReleaseDto(
                release.getVersion(),
                release.getBaseUrl(),
                release.getBaseUrl() + "index.html",
                release.getBaseUrl() + "Build/game.loader.js",
                release.getBaseUrl() + "asset-manifest.json",
                release.getFileCount(),
                release.getTotalSizeBytes(),
                release.getPublishedAt()
        );
    }

    private record UploadFile(String relativePath, MultipartFile file) {
    }
}

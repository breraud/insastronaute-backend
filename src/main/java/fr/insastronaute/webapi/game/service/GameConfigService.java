package fr.insastronaute.webapi.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.game.config.GameConfigProperties;
import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.game.dto.GameConfigImportDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GameConfigService {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+\\.json$");
    private static final Pattern FOLDER_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$");
    private static final String LATEST_RELEASE_FILE = "latest.json";
    private static final int MAX_IMPORTED_FILES = 2_000;
    private static final long MAX_IMPORTED_BYTES = 64L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;
    private final String objectPrefix;
    private final String releasesObjectPrefix;

    public GameConfigService(
            S3Client s3Client,
            S3Properties s3Properties,
            ObjectMapper objectMapper,
            GameConfigProperties gameConfigProperties
    ) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.objectMapper = objectMapper;
        this.objectPrefix = normalizePrefix(gameConfigProperties.getObjectPrefix());
        this.releasesObjectPrefix = normalizePrefix(gameConfigProperties.getReleasesObjectPrefix());
    }

    public List<String> list() {
        List<String> fileNames = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(objectPrefix)
                    .continuationToken(continuationToken)
                    .build());

            response.contents().stream()
                    .map(object -> object.key().substring(objectPrefix.length()))
                    .filter(this::isValidFilePath)
                    .forEach(fileNames::add);

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return fileNames.stream().sorted().toList();
    }

    public List<String> listFolders() {
        Set<String> folders = new LinkedHashSet<>();
        String continuationToken = null;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(objectPrefix)
                    .continuationToken(continuationToken)
                    .build());

            for (S3Object object : response.contents()) {
                collectFolders(object.key().substring(objectPrefix.length()), folders);
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return folders.stream().sorted().toList();
    }

    public String read(String fileName) {
        return readObject(objectKey(fileName), "Configuration de jeu introuvable.");
    }

    public String readReleaseFile(String version, String fileName) {
        String objectKey = releasePrefix(validateVersion(version)) + validateFilePath(fileName);
        return readObject(objectKey, "Configuration de jeu publiée introuvable.");
    }

    private String readObject(String objectKey, String notFoundMessage) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());

            return bytes.asString(StandardCharsets.UTF_8);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new NotFoundException(notFoundMessage);
            }
            throw exception;
        }
    }

    public void write(String fileName, String content) {
        String objectKey = objectKey(fileName);
        validateJson(content);

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType("application/json; charset=utf-8")
                        .cacheControl("no-cache")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8));
    }

    public void createFolder(String folderPath) {
        String normalizedFolderPath = validateFolderPath(folderPath);
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectPrefix + normalizedFolderPath + "/")
                        .contentType("application/x-directory")
                        .cacheControl("no-cache")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromBytes(new byte[0]));
    }

    public void deleteFile(String fileName) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .key(objectKey(fileName))
                .build());
    }

    public void deleteFolder(String folderPath) {
        String normalizedFolderPath = validateFolderPath(folderPath);
        int deletedCount = deletePrefix(objectPrefix + normalizedFolderPath + "/");
        if (deletedCount == 0) {
            throw new NotFoundException("Dossier de configuration de jeu introuvable.");
        }
    }

    /**
     * Toute la configuration editable dans une archive zip.
     *
     * <p>Les dossiers vides y figurent comme entrees de repertoire : l'editeur les
     * affiche, et un aller-retour qui les perdrait ne rendrait pas ce qu'il a pris.</p>
     */
    public byte[] exportArchive() {
        List<String> files = list();
        List<String> folders = listFolders();
        if (files.isEmpty() && folders.isEmpty()) {
            throw new NotFoundException("Aucune configuration de jeu a exporter.");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (String folder : folders) {
                archive.putNextEntry(new ZipEntry(folder + "/"));
                archive.closeEntry();
            }
            for (String file : files) {
                archive.putNextEntry(new ZipEntry(file));
                archive.write(read(file).getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Impossible de construire l'archive de configuration.", exception);
        }
        return buffer.toByteArray();
    }

    /**
     * Reinstaller une configuration exportee.
     *
     * <p>L'archive est entierement lue et validee avant la moindre ecriture. Une
     * configuration a moitie importee -- quelques fichiers du nouvel etat, le reste de
     * l'ancien -- est pire qu'un import refuse : elle ne correspond a aucune version
     * connue, et rien ne dit ou la coupure a eu lieu.</p>
     *
     * <p>{@code replace} efface d'abord tout l'existant. Sans lui, l'import recouvre les
     * fichiers homonymes et laisse les autres en place : c'est le comportement sur, mais
     * il ne peut pas supprimer un fichier, donc il ne reproduit pas fidelement l'etat
     * exporte.</p>
     */
    public GameConfigImportDto importArchive(MultipartFile archive, boolean replace) {
        if (archive == null || archive.isEmpty()) {
            throw new BadRequestException("L'archive de configuration est requise.");
        }

        Map<String, String> files = new LinkedHashMap<>();
        Set<String> folders = new LinkedHashSet<>();
        long totalBytes = 0;

        try (ZipInputStream stream = new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String rawName = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || rawName.endsWith("/")) {
                    folders.add(validateFolderPath(rawName.substring(0, rawName.length() - 1)));
                    continue;
                }

                if (files.size() >= MAX_IMPORTED_FILES) {
                    throw new BadRequestException("L'archive contient trop de fichiers de configuration.");
                }

                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                totalBytes += content.length();
                // La taille decompressee est ce qui compte : une archive minuscule peut
                // se deployer en gigaoctets.
                if (totalBytes > MAX_IMPORTED_BYTES) {
                    throw new BadRequestException("L'archive de configuration est trop volumineuse.");
                }

                String filePath = validateFilePath(rawName);
                validateJson(content);
                if (files.put(filePath, content) != null) {
                    throw new BadRequestException("L'archive contient deux fois le fichier " + filePath + ".");
                }
            }
        } catch (IOException exception) {
            throw new BadRequestException("L'archive de configuration est illisible.");
        }

        if (files.isEmpty()) {
            throw new BadRequestException("L'archive ne contient aucune configuration JSON.");
        }

        int deleted = replace ? deletePrefix(objectPrefix) : 0;
        folders.forEach(this::createFolder);
        files.forEach(this::write);
        return new GameConfigImportDto(files.size(), folders.size(), replace, deleted);
    }

    public GameConfigReleaseDto release(String version) {
        String normalizedVersion = validateVersion(version);
        List<String> files = list();
        if (files.isEmpty()) {
            throw new BadRequestException("Aucune configuration JSON courante à publier.");
        }

        String releasePrefix = releasePrefix(normalizedVersion);
        for (String file : files) {
            String content = read(file);
            putJsonObject(releasePrefix + file, content, "public, max-age=31536000, immutable");
        }

        GameConfigReleaseDto release = new GameConfigReleaseDto(
                normalizedVersion,
                releasePrefix,
                files,
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()
        );
        putJsonObject(latestReleaseObjectKey(), serializeRelease(release), "no-cache");

        return release;
    }

    public GameConfigReleaseDto releaseUploaded(String version, List<MultipartFile> files) {
        String normalizedVersion = validateVersion(version);
        List<UploadedConfigFile> uploadedFiles = validateUploadedFiles(files);
        String releasePrefix = releasePrefix(normalizedVersion);

        List<String> filePaths = new ArrayList<>();
        for (UploadedConfigFile uploadedFile : uploadedFiles) {
            putJsonObject(releasePrefix + uploadedFile.relativePath(), uploadedFile.content(), "public, max-age=31536000, immutable");
            filePaths.add(uploadedFile.relativePath());
        }

        List<String> sortedFilePaths = filePaths.stream().sorted().toList();
        GameConfigReleaseDto release = new GameConfigReleaseDto(
                normalizedVersion,
                releasePrefix,
                sortedFilePaths,
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()
        );
        putJsonObject(latestReleaseObjectKey(), serializeRelease(release), "no-cache");

        return release;
    }

    public List<GameConfigReleaseDto> listReleases() {
        Map<String, ReleaseAccumulator> releases = new LinkedHashMap<>();
        String continuationToken = null;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(releasesObjectPrefix)
                    .continuationToken(continuationToken)
                    .build());

            for (S3Object object : response.contents()) {
                collectReleaseObject(object, releases);
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return releases.values().stream()
                .map(ReleaseAccumulator::toDto)
                .sorted(this::compareReleasesNewestFirst)
                .toList();
    }

    public GameConfigReleaseDto latestRelease() {
        GameConfigReleaseDto latestRelease = readLatestReleaseOrNull();
        if (latestRelease == null) {
            throw new NotFoundException("Aucune release de configuration de jeu n'est publiée.");
        }

        return latestRelease;
    }

    public void deleteRelease(String version) {
        String normalizedVersion = validateVersion(version);
        String releasePrefix = releasePrefix(normalizedVersion);
        int deletedCount = deletePrefix(releasePrefix);
        if (deletedCount == 0) {
            throw new NotFoundException("Release de configuration de jeu introuvable.");
        }

        GameConfigReleaseDto latestRelease = readLatestReleaseOrNull();
        if (latestRelease != null && normalizedVersion.equals(latestRelease.version())) {
            promotePreviousReleaseOrDeleteLatest();
        }
    }

    private void promotePreviousReleaseOrDeleteLatest() {
        List<GameConfigReleaseDto> remainingReleases = listReleases();
        if (remainingReleases.isEmpty()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(latestReleaseObjectKey())
                    .build());
            return;
        }

        putJsonObject(latestReleaseObjectKey(), serializeRelease(remainingReleases.getFirst()), "no-cache");
    }

    private GameConfigReleaseDto readLatestReleaseOrNull() {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(latestReleaseObjectKey())
                    .build());

            return objectMapper.readValue(bytes.asString(StandardCharsets.UTF_8), GameConfigReleaseDto.class);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("La release latest des configurations de jeu est invalide.");
        }
    }

    private String objectKey(String fileName) {
        return objectPrefix + validateFilePath(fileName);
    }

    private String releasePrefix(String version) {
        return releasesObjectPrefix + version + "/";
    }

    private String latestReleaseObjectKey() {
        return releasesObjectPrefix + LATEST_RELEASE_FILE;
    }

    private void collectReleaseObject(S3Object object, Map<String, ReleaseAccumulator> releases) {
        String relativePath = object.key().substring(releasesObjectPrefix.length());
        if (relativePath.isBlank() || relativePath.equals(LATEST_RELEASE_FILE) || relativePath.endsWith("/")) {
            return;
        }

        int slashIndex = relativePath.indexOf('/');
        if (slashIndex <= 0 || slashIndex == relativePath.length() - 1) {
            return;
        }

        String version = relativePath.substring(0, slashIndex);
        String filePath = relativePath.substring(slashIndex + 1);
        if (!isValidVersion(version) || !isValidFilePath(filePath)) {
            return;
        }

        releases.computeIfAbsent(version, ReleaseAccumulator::new)
                .addFile(filePath, object.lastModified());
    }

    private int compareReleasesNewestFirst(GameConfigReleaseDto left, GameConfigReleaseDto right) {
        Instant leftReleasedAt = left.releasedAt();
        Instant rightReleasedAt = right.releasedAt();
        if (leftReleasedAt != null && rightReleasedAt != null) {
            int releasedAtComparison = rightReleasedAt.compareTo(leftReleasedAt);
            if (releasedAtComparison != 0) {
                return releasedAtComparison;
            }
        } else if (leftReleasedAt != null) {
            return -1;
        } else if (rightReleasedAt != null) {
            return 1;
        }

        return right.version().compareTo(left.version());
    }

    private boolean isValidVersion(String version) {
        try {
            validateVersion(version);
            return true;
        } catch (BadRequestException exception) {
            return false;
        }
    }

    private String validateVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new BadRequestException("La version de configuration de jeu est requise.");
        }
        String normalizedVersion = version.trim();
        if (!VERSION_PATTERN.matcher(normalizedVersion).matches()) {
            throw new BadRequestException("La version de configuration de jeu doit contenir uniquement lettres, chiffres, points, tirets ou underscores.");
        }
        if (LATEST_RELEASE_FILE.equals(normalizedVersion + ".json")) {
            throw new BadRequestException("La version de configuration de jeu est réservée.");
        }
        return normalizedVersion;
    }

    private String serializeRelease(GameConfigReleaseDto release) {
        try {
            return objectMapper.writeValueAsString(release);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Impossible de sérialiser la release de configuration de jeu.");
        }
    }

    private void validateJson(String content) {
        if (content == null) {
            throw new BadRequestException("Le contenu JSON est invalide.");
        }

        try {
            objectMapper.readTree(content);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BadRequestException("Le contenu JSON est invalide.");
        }
    }

    private List<UploadedConfigFile> validateUploadedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Le dossier de configurations JSON est requis.");
        }

        List<UploadedConfigFile> uploadedFiles = new ArrayList<>();
        Set<String> relativePaths = new HashSet<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String relativePath = normalizeUploadedFilePath(file.getOriginalFilename());
            if (!relativePaths.add(relativePath)) {
                throw new BadRequestException("Le dossier de configurations contient deux fichiers avec le même chemin: " + relativePath);
            }

            String content = readUploadedFile(file);
            validateJson(content);
            uploadedFiles.add(new UploadedConfigFile(relativePath, content));
        }

        if (uploadedFiles.isEmpty()) {
            throw new BadRequestException("Le dossier de configurations JSON ne contient aucun fichier.");
        }

        return uploadedFiles;
    }

    private String normalizeUploadedFilePath(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Chaque configuration JSON doit avoir un chemin relatif.");
        }

        String rawPath = originalFilename.replace('\\', '/');
        if (rawPath.contains("\0")) {
            throw new BadRequestException("Le chemin d'une configuration JSON est invalide.");
        }

        while (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }

        Path normalizedPath = Path.of(rawPath).normalize();
        String relativePath = normalizedPath.toString().replace('\\', '/');
        if (relativePath.isBlank() || relativePath.startsWith("../") || relativePath.equals("..") || relativePath.startsWith("/")) {
            throw new BadRequestException("Le chemin d'une configuration JSON sort du dossier autorisé.");
        }

        return validateFilePath(relativePath);
    }

    private String readUploadedFile(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire une configuration JSON uploadée.");
        }
    }

    private void putJsonObject(String objectKey, String content, String cacheControl) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType("application/json; charset=utf-8")
                        .cacheControl(cacheControl)
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8));
    }

    private void collectFolders(String relativePath, Set<String> folders) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        String normalizedPath = stripLeadingSlash(relativePath);
        if (normalizedPath.endsWith("/")) {
            String folderPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            if (isValidFolderPath(folderPath)) {
                addParentFolders(folderPath, folders);
            }
            return;
        }

        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash > 0) {
            String parentFolder = normalizedPath.substring(0, lastSlash);
            if (isValidFolderPath(parentFolder)) {
                addParentFolders(parentFolder, folders);
            }
        }
    }

    private void addParentFolders(String folderPath, Set<String> folders) {
        String[] segments = folderPath.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String segment : segments) {
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }
            currentPath.append(segment);
            folders.add(currentPath.toString());
        }
    }

    private boolean isValidFilePath(String filePath) {
        try {
            validateFilePath(filePath);
            return true;
        } catch (BadRequestException exception) {
            return false;
        }
    }

    private String validateFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BadRequestException("Le chemin de fichier est requis.");
        }

        String normalizedPath = stripLeadingSlash(filePath.trim().replace('\\', '/'));
        if (normalizedPath.contains("//") || normalizedPath.endsWith("/") || normalizedPath.contains("../")) {
            throw new BadRequestException("Le chemin de fichier est invalide.");
        }

        String[] segments = normalizedPath.split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if (!FOLDER_SEGMENT_PATTERN.matcher(segments[index]).matches()) {
                throw new BadRequestException("Chaque dossier doit contenir uniquement lettres, chiffres, tirets ou underscores.");
            }
        }

        String fileName = segments[segments.length - 1];
        if (!FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new BadRequestException("Le nom de fichier doit contenir uniquement lettres, chiffres, tirets, underscores et finir par .json.");
        }

        return normalizedPath;
    }

    private boolean isValidFolderPath(String folderPath) {
        try {
            validateFolderPath(folderPath);
            return true;
        } catch (BadRequestException exception) {
            return false;
        }
    }

    private String validateFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new BadRequestException("Le chemin de dossier est requis.");
        }

        String normalizedPath = stripLeadingSlash(folderPath.trim().replace('\\', '/'));
        while (normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        if (normalizedPath.isBlank() || normalizedPath.contains("//") || normalizedPath.contains("../")) {
            throw new BadRequestException("Le chemin de dossier est invalide.");
        }

        for (String segment : normalizedPath.split("/")) {
            if (!FOLDER_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new BadRequestException("Chaque dossier doit contenir uniquement lettres, chiffres, tirets ou underscores.");
            }
        }

        return normalizedPath;
    }

    private String stripLeadingSlash(String path) {
        String normalizedPath = path;
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    private int deletePrefix(String prefix) {
        String continuationToken = null;
        int deletedCount = 0;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());

            List<S3Object> objects = response.contents();
            if (!objects.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .delete(Delete.builder()
                                .objects(objects.stream()
                                        .map(object -> ObjectIdentifier.builder()
                                                .key(object.key())
                                                .build())
                                        .toList())
                                .build())
                        .build());
                deletedCount += objects.size();
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return deletedCount;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new BadRequestException("Le préfixe S3 des configurations de jeu est requis.");
        }

        String normalizedPrefix = prefix.trim().replace('\\', '/');
        while (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        if (!normalizedPrefix.endsWith("/")) {
            normalizedPrefix += "/";
        }
        if (normalizedPrefix.contains("../") || normalizedPrefix.equals("..") || normalizedPrefix.contains("//")) {
            throw new BadRequestException("Le préfixe S3 des configurations de jeu est invalide.");
        }

        return normalizedPrefix;
    }

    private final class ReleaseAccumulator {
        private final String version;
        private final List<String> files = new ArrayList<>();
        private Instant releasedAt;

        private ReleaseAccumulator(String version) {
            this.version = version;
        }

        private void addFile(String filePath, Instant lastModified) {
            files.add(filePath);
            if (lastModified != null && (releasedAt == null || lastModified.isAfter(releasedAt))) {
                releasedAt = lastModified;
            }
        }

        private GameConfigReleaseDto toDto() {
            return new GameConfigReleaseDto(
                    version,
                    releasePrefix(version),
                    files.stream().sorted().toList(),
                    releasedAt
            );
        }
    }

    private record UploadedConfigFile(String relativePath, String content) {
    }
}

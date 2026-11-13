package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TreasureHuntStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm", "video/quicktime", "application/pdf"
    );
    private static final Map<String, String> SAFE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "video/mp4", ".mp4",
            "video/webm", ".webm",
            "video/quicktime", ".mov",
            "application/pdf", ".pdf"
    );
    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024;
    private static final Tika TIKA = new Tika();

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public TreasureHuntStorageService(S3Client s3Client, S3Presigner s3Presigner, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
    }

    public String store(MultipartFile file) {
        byte[] content = readAndValidate(file);
        String detectedContentType = detectContentType(content);
        String declaredContentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(detectedContentType)
                || !detectedContentType.equals(declaredContentType)) {
            throw new BadRequestException("Le contenu du fichier de récompense ne correspond pas à son type déclaré.");
        }
        String objectKey = "treasure-hunt/" + UUID.randomUUID() + "/"
                + safeStoredFileName(file.getOriginalFilename(), detectedContentType);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(objectKey)
                            .contentType(detectedContentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            throw new StorageUnavailableException("Le stockage des récompenses est temporairement indisponible.", exception);
        }
        return objectKey;
    }

    public String createReadUrl(String storagePath) {
        if (storagePath == null || !storagePath.startsWith("treasure-hunt/") || storagePath.contains("..")) {
            throw new BadRequestException("La clé de récompense est invalide.");
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(storagePath).build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier de récompense est requis.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Le fichier de récompense ne peut pas dépasser 15 MB.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Type de fichier de récompense non pris en charge.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le fichier de récompense.");
        }
    }

    private String detectContentType(byte[] content) {
        return normalizeContentType(TIKA.detect(content));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String safeStoredFileName(String fileName, String detectedContentType) {
        String sanitized = sanitizeFileName(fileName);
        int extensionIndex = sanitized.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? sanitized.substring(0, extensionIndex) : sanitized;
        return baseName + SAFE_EXTENSIONS.get(detectedContentType);
    }

    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "reward" : fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..") ? "reward" : sanitized;
    }
}

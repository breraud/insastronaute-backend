package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Service
public class DeckImageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public DeckImageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public String store(UUID deckId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("L'image du deck est requise.");
        }

        String fileName = image.getOriginalFilename();
        String objectKey = "decks/" + deckId + "/" + UUID.randomUUID() + "/" + sanitizeFileName(fileName);

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire l'image du deck.");
        }

        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(s3Properties.getPublicBucket())
                            .key(objectKey)
                            .contentType(contentType(image))
                            .cacheControl("public, max-age=31536000, immutable")
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromBytes(imageBytes));
        } catch (SdkException exception) {
            throw new StorageUnavailableException("Le stockage de l'image du deck est temporairement indisponible.", exception);
        }

        return publicUrl(objectKey);
    }

    /**
     * Retire les visuels d'un pack supprimé.
     *
     * Chaque enregistrement d'image écrit sous un nouveau préfixe aléatoire, donc
     * un pack souvent réillustré en laisse plusieurs : on balaie tout ce qui vit
     * sous son identifiant. Au pire échec, le pack disparaît quand même et il
     * reste un objet orphelin — l'inverse laisserait un pack qu'on ne peut plus
     * supprimer parce que son image résiste.
     */
    public void deleteAll(UUID deckId) {
        try {
            ListObjectsV2Response listing = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix("decks/" + deckId + "/")
                    .build());
            for (S3Object object : listing.contents()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(object.key())
                        .build());
            }
        } catch (RuntimeException ignored) {
            // Best-effort : la suppression du pack ne dépend pas du stockage.
        }
    }

    public String publicUrlForStoredValue(String storedImageUrl) {
        if (storedImageUrl == null || storedImageUrl.isBlank()) {
            return storedImageUrl;
        }
        String trimmedUrl = storedImageUrl.trim();
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl;
        }
        if (trimmedUrl.startsWith("/public/")) {
            return publicUrl(trimmedUrl.substring("/public/".length()));
        }
        if (trimmedUrl.startsWith("public/")) {
            return publicUrl(trimmedUrl.substring("public/".length()));
        }
        if (trimmedUrl.startsWith("decks/")) {
            return publicUrl(trimmedUrl);
        }
        return trimmedUrl;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "deck-image";
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return "deck-image";
        }
        return sanitized;
    }

    private String contentType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier une image de deck.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }
}

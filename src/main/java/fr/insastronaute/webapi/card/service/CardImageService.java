package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.storage.S3Properties;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class CardImageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int DISPLAY_MAX_WIDTH = 1080;
    private static final int DISPLAY_MAX_HEIGHT = 1512;
    private static final float DISPLAY_QUALITY = 0.88F;
    private static final int THUMBNAIL_MAX_WIDTH = 420;
    private static final int THUMBNAIL_MAX_HEIGHT = 588;
    private static final float THUMBNAIL_QUALITY = 0.82F;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public CardImageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public StoredCardImages store(UUID cardId, MultipartFile image) {
        return storeImages(
                image,
                "cards/" + cardId,
                "Une image de carte est requise.",
                "L'image de carte ne doit pas dépasser 20 Mo.",
                "Impossible de lire l'image de carte.",
                "Impossible de compresser l'image de carte.",
                "Impossible de générer l'aperçu de carte.",
                "Le stockage des images de cartes est temporairement indisponible."
        );
    }

    public StoredCardImages storeCategory(UUID categoryId, MultipartFile image) {
        return storeImages(
                image,
                "card-categories/" + categoryId,
                "Une image de catégorie est requise.",
                "L'image de catégorie ne doit pas dépasser 20 Mo.",
                "Impossible de lire l'image de catégorie.",
                "Impossible de compresser l'image de catégorie.",
                "Impossible de générer l'aperçu de catégorie.",
                "Le stockage des images de catégories est temporairement indisponible."
        );
    }

    private StoredCardImages storeImages(
            MultipartFile image,
            String objectPrefix,
            String missingImageMessage,
            String maxSizeMessage,
            String readFailureMessage,
            String displayFailureMessage,
            String thumbnailFailureMessage,
            String storageFailureMessage
    ) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException(missingImageMessage);
        }

        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BadRequestException(maxSizeMessage);
        }

        byte[] originalBytes;
        try {
            originalBytes = image.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException(readFailureMessage);
        }

        DecodedCardImage decodedImage = decodeImage(originalBytes);
        validateDeclaredContentType(image.getContentType(), decodedImage.contentType());

        String version = UUID.randomUUID().toString();
        String originalKey = objectPrefix + "/" + version + "/original.jpg";
        String thumbnailKey = objectPrefix + "/" + version + "/thumbnail.jpg";
        byte[] displayBytes = createJpegImage(
                decodedImage.image(),
                DISPLAY_MAX_WIDTH,
                DISPLAY_MAX_HEIGHT,
                DISPLAY_QUALITY,
                displayFailureMessage
        );
        byte[] thumbnailBytes = createJpegImage(
                decodedImage.image(),
                THUMBNAIL_MAX_WIDTH,
                THUMBNAIL_MAX_HEIGHT,
                THUMBNAIL_QUALITY,
                thumbnailFailureMessage
        );

        try {
            putJpegObject(originalKey, displayBytes);
            putJpegObject(thumbnailKey, thumbnailBytes);
        } catch (SdkException exception) {
            deleteQuietly(originalKey);
            deleteQuietly(thumbnailKey);
            throw new StorageUnavailableException(storageFailureMessage, exception);
        }

        return new StoredCardImages(
                originalKey,
                thumbnailKey,
                publicUrl(originalKey),
                publicUrl(thumbnailKey),
                "image/jpeg"
        );
    }

    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException ignored) {
            // Card metadata should not be blocked by a stale object cleanup failure.
        }
    }

    private DecodedCardImage decodeImage(byte[] imageBytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                throw new BadRequestException("L'image de carte est invalide.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("L'image de carte est invalide.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String contentType = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new BadRequestException("Seules les images JPEG et PNG sont autorisées.");
                };
                return new DecodedCardImage(reader.read(0), contentType);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BadRequestException("L'image de carte est invalide.");
        }
    }

    private void validateDeclaredContentType(String declaredContentType, String detectedContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return;
        }

        String normalizedDeclaredContentType = switch (declaredContentType.toLowerCase(Locale.ROOT)) {
            case "image/jpg", "image/pjpeg" -> "image/jpeg";
            case "image/jpeg", "image/png" -> declaredContentType.toLowerCase(Locale.ROOT);
            default -> throw new BadRequestException("Seules les images JPEG et PNG sont autorisées.");
        };
        if (!normalizedDeclaredContentType.equals(detectedContentType)) {
            throw new BadRequestException("Le type de l'image ne correspond pas au fichier envoyé.");
        }
    }

    private byte[] createJpegImage(
            BufferedImage source,
            int maxWidth,
            int maxHeight,
            float quality,
            String errorMessage
    ) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Thumbnails.of(source)
                    .size(maxWidth, maxHeight)
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException(errorMessage);
        }
    }

    private void putJpegObject(String objectKey, byte[] bytes) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType("image/jpeg")
                        .cacheControl("public, max-age=31536000, immutable")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier une image de carte.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }

    public record StoredCardImages(
            String imageObjectKey,
            String thumbnailObjectKey,
            String imageUrl,
            String thumbnailUrl,
            String contentType
    ) {
    }

    private record DecodedCardImage(BufferedImage image, String contentType) {
    }
}

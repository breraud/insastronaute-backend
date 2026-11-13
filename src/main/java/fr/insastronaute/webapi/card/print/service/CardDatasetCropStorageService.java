package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Depose les decoupes du dataset, <strong>telles quelles</strong>.
 *
 * <p>Aucune conversion, aucun redimensionnement, aucune recompression : les octets deposes
 * sont exactement ceux qui ont ete envoyes. La seule lecture faite de l'image est celle de
 * ses dimensions, qui sert de metadonnee et ne modifie rien.
 */
@Service
public class CardDatasetCropStorageService {

    private static final long MAX_CROP_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String CROP_PREFIX = "card-dataset/crops/";
    // Un prefixe distinct, et pas un suffixe dans le meme dossier : les photos entieres pesent
    // dix fois les decoupes, et une regle de cycle de vie ou un menage doit pouvoir les viser
    // sans emporter le jeu de donnees lui-meme.
    private static final String FRAME_PREFIX = "card-dataset/frames/";
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp"
    );

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public CardDatasetCropStorageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public StoredCrop store(UUID cropId, String sheetId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("La decoupe est requise.");
        }
        return put(CROP_PREFIX, cropId, sheetId, image, "La decoupe");
    }

    /**
     * La photo entiere dont une decoupe a ete tiree.
     *
     * <p>Elle n'est pas le jeu de donnees de lecture : elle sert a comprendre pourquoi le
     * cadrage automatique a echoue, et a entrainer un jour ce qui le remplacera. Elle est donc
     * facultative de bout en bout, et son absence n'invalide rien.
     */
    public StoredCrop storeFrame(UUID cropId, String sheetId, MultipartFile frame) {
        if (frame == null || frame.isEmpty()) {
            throw new BadRequestException("La photo entiere est requise.");
        }
        return put(FRAME_PREFIX, cropId, sheetId, frame, "La photo entiere");
    }

    private StoredCrop put(String prefix, UUID cropId, String sheetId, MultipartFile image, String subject) {
        if (image.getSize() > MAX_CROP_SIZE_BYTES) {
            throw new BadRequestException(subject + " ne doit pas depasser 10 Mo.");
        }
        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BadRequestException(subject + " doit etre une image PNG, JPEG ou WebP.");
        }

        byte[] rawBytes;
        try {
            rawBytes = image.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire " + subject.toLowerCase() + ".");
        }
        if (rawBytes.length == 0) {
            throw new BadRequestException(subject + " est vide.");
        }

        String objectKey = prefix + sheetId + "/" + cropId + "." + extension;
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .cacheControl("private, max-age=0, no-cache")
                        .acl(ObjectCannedACL.PRIVATE)
                        .build(),
                RequestBody.fromBytes(rawBytes));

        Dimensions dimensions = readDimensions(rawBytes);
        return new StoredCrop(objectKey, contentType, (long) rawBytes.length, dimensions.width(), dimensions.height());
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("La cle de fichier est requise.");
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .key(objectKey)
                .build());
    }

    public byte[] read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("La cle de fichier est requise.");
        }
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .key(objectKey)
                .build());
        return bytes.asByteArray();
    }

    private Dimensions readDimensions(byte[] rawBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (image == null) {
                return new Dimensions(null, null);
            }
            return new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException | RuntimeException exception) {
            return new Dimensions(null, null);
        }
    }

    public record StoredCrop(String objectKey, String contentType, Long byteSize, Integer widthPx, Integer heightPx) {
    }

    private record Dimensions(Integer width, Integer height) {
    }
}

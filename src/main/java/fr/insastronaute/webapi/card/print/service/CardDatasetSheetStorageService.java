package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class CardDatasetSheetStorageService {

    private static final String SHEET_PREFIX = "card-print/dataset-sheets/";
    private static final String STORAGE_UNAVAILABLE =
            "Le stockage des planches de dataset est temporairement indisponible.";

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public CardDatasetSheetStorageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public String store(UUID sheetId, byte[] pdf) {
        String objectKey = SHEET_PREFIX + sheetId + ".pdf";
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(s3Properties.getPublicBucket())
                            .key(objectKey)
                            .contentType("application/pdf")
                            .cacheControl("private, max-age=0, no-cache")
                            .acl(ObjectCannedACL.PRIVATE)
                            .build(),
                    RequestBody.fromBytes(pdf));
        } catch (SdkException exception) {
            throw new StorageUnavailableException(STORAGE_UNAVAILABLE, exception);
        }
        return objectKey;
    }

    /**
     * Lit un PDF de planche.
     *
     * <p>Un objet absent n'est pas une panne de stockage : la distinction compte, parce qu'un
     * 503 << stockage temporairement indisponible >> envoie chercher un incident d'infra
     * inexistant, alors qu'un 404 nomme la cle qui manque et pointe la vraie question -- qui
     * l'a effacee, ou pourquoi elle n'a jamais ete ecrite.
     */
    public byte[] read(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
            return response.asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new NotFoundException("Le PDF de planche " + objectKey + " est introuvable dans le stockage.");
        } catch (SdkException exception) {
            throw new StorageUnavailableException(STORAGE_UNAVAILABLE, exception);
        }
    }

    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException exception) {
            throw new StorageUnavailableException(STORAGE_UNAVAILABLE, exception);
        }
    }
}

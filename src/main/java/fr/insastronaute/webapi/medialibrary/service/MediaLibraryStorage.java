package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;

/**
 * Le stockage des preuves, vu de la Mediatheque : un objet absent est un 404, une panne
 * un 503. Ni la cle ni le bucket ne remontent jamais dans un message.
 */
@Component
public class MediaLibraryStorage {

    static final String MEDIA_NOT_FOUND = "Média introuvable.";
    private static final String STORAGE_DOWN = "Le stockage des médias ne répond pas.";

    private final ProofStorageService proofStorage;

    public MediaLibraryStorage(ProofStorageService proofStorage) {
        this.proofStorage = proofStorage;
    }

    public ProofMediaStream open(String objectKey, String range) {
        try {
            return proofStorage.openProofMedia(objectKey, range);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new NotFoundException(MEDIA_NOT_FOUND);
            }
            throw new StorageUnavailableException(STORAGE_DOWN, exception);
        } catch (SdkException exception) {
            throw new StorageUnavailableException(STORAGE_DOWN, exception);
        }
    }

    public Optional<InputStream> openIfPresent(String objectKey) {
        try {
            return Optional.of(open(objectKey, null).inputStream());
        } catch (NotFoundException exception) {
            return Optional.empty();
        }
    }

    /**
     * Abandonner un flux de stockage qui n'a pas ete lu jusqu'au bout.
     *
     * <p>Fermer un ResponseInputStream du SDK S3 (client Apache) lit d'abord tout le reste
     * de l'objet pour rendre la connexion au pool. Une tuile video que le navigateur coupe
     * des la premiere image, ou un export interrompu, liraient ainsi jusqu'a 40 Mio pour
     * rien. L'abandon coupe la requete S3 ; la fermeture qui suit ne lit plus rien. A
     * appeler avant {@code close()}, et seulement sur un flux qui s'arrete en route : un
     * flux lu jusqu'au bout rend sa connexion au pool en se fermant.</p>
     */
    public static void abandon(InputStream input) {
        if (input instanceof Abortable abortable) {
            try {
                abortable.abort();
            } catch (RuntimeException ignored) {
                // L'erreur d'origine est celle qui compte ; la fermeture suit de toute facon.
            }
        }
    }

    public long sizeOrZero(String objectKey) {
        try {
            return proofStorage.proofObjectSize(objectKey);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return 0;
            }
            throw new StorageUnavailableException(STORAGE_DOWN, exception);
        } catch (SdkException exception) {
            throw new StorageUnavailableException(STORAGE_DOWN, exception);
        }
    }
}

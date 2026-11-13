package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.proof.domain.ProofUpload;
import fr.insastronaute.webapi.proof.repository.ProofUploadRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** La taille d'un fichier de preuve, telle que l'envoi l'a mesuree. */
@Component
public class MediaSizeLookup {

    private final ProofUploadRepository uploads;

    public MediaSizeLookup(ProofUploadRepository uploads) {
        this.uploads = uploads;
    }

    public Map<String, Long> sizesOf(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> sizes = new HashMap<>();
        for (ProofUpload upload : uploads.findByObjectKeyIn(objectKeys)) {
            if (upload.getSizeBytes() != null) {
                sizes.putIfAbsent(upload.getObjectKey(), upload.getSizeBytes());
            }
        }
        return sizes;
    }
}

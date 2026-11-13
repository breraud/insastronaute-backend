package fr.insastronaute.webapi.proof.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProofDirectUploadFileRequest(
        String originalFileName,

        @NotBlank(message = "Le type de fichier est requis.")
        String contentType,

        @Positive(message = "La taille du fichier doit être supérieure à 0.")
        long sizeBytes
) {
}

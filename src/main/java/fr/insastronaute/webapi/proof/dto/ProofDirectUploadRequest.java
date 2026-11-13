package fr.insastronaute.webapi.proof.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ProofDirectUploadRequest(
        @NotNull(message = "Le défi est requis.")
        UUID challengeId,

        String originalFileName,

        @NotBlank(message = "Le type de fichier est requis.")
        String contentType,

        @Positive(message = "La taille du fichier doit être supérieure à 0.")
        long sizeBytes
) {
}

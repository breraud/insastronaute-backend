package fr.insastronaute.webapi.proof.dto;

import java.util.UUID;

public record ProofFileUploadResponse(
        UUID uploadId,
        long remainingSizeBytes
) {
}

package fr.insastronaute.webapi.proof.dto;

import java.time.Instant;
import java.util.UUID;

public record ProofDraftDto(
        UUID uploadId,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String status,
        Instant createdAt,
        String previewUrl
) {
}

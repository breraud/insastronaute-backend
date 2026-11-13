package fr.insastronaute.webapi.proof.dto;

import java.util.Map;
import java.util.UUID;

public record ProofDirectUploadResponse(
        UUID uploadId,
        String uploadUrl,
        Map<String, String> fields,
        Integer expiresInSeconds
) {
}

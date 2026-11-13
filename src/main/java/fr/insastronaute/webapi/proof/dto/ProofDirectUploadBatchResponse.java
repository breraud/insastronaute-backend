package fr.insastronaute.webapi.proof.dto;

import java.util.List;

public record ProofDirectUploadBatchResponse(
        List<ProofDirectUploadResponse> files
) {
}

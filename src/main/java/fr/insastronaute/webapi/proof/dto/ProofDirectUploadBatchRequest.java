package fr.insastronaute.webapi.proof.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProofDirectUploadBatchRequest(
        @NotNull(message = "Le défi est requis.")
        UUID challengeId,

        @NotEmpty(message = "Au moins un fichier de preuve est requis.")
        @Size(max = 10, message = "Une preuve ne peut pas contenir plus de 10 fichiers.")
        List<@Valid ProofDirectUploadFileRequest> files
) {
}

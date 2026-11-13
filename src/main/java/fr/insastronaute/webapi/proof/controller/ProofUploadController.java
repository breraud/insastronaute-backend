package fr.insastronaute.webapi.proof.controller;

import fr.insastronaute.webapi.proof.dto.ProofDirectUploadBatchRequest;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadBatchResponse;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadRequest;
import fr.insastronaute.webapi.proof.dto.ProofDirectUploadResponse;
import fr.insastronaute.webapi.proof.dto.ProofDraftParticipantsRequest;
import fr.insastronaute.webapi.proof.dto.ProofDraftResponse;
import fr.insastronaute.webapi.proof.dto.ProofFileUploadResponse;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/uploads/proofs")
@Tag(name = "Proof Uploads")
public class ProofUploadController {

    private final ProofStorageService proofStorageService;

    public ProofUploadController(ProofStorageService proofStorageService) {
        this.proofStorageService = proofStorageService;
    }

    @PostMapping("/direct")
    @Operation(summary = "Create a proof upload", description = "Creates a draft proof file record and returns the direct-upload information for object storage.")
    public ProofDirectUploadResponse createDirectUpload(
            @Valid @RequestBody ProofDirectUploadRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return proofStorageService.createDirectUpload(oidcUser.getSubject(), request);
    }

    @PostMapping("/direct/batch")
    @Operation(summary = "Create proof uploads", description = "Creates several draft proof file records and returns direct-upload information for each file.")
    public ProofDirectUploadBatchResponse createDirectUploadBatch(
            @Valid @RequestBody ProofDirectUploadBatchRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return proofStorageService.createDirectUploads(oidcUser.getSubject(), request);
    }

    @PostMapping("/{uploadId}/complete")
    @Operation(summary = "Complete a proof upload", description = "Marks a direct proof upload as complete after the browser has uploaded the file to object storage.")
    public ProofFileUploadResponse completeDirectUpload(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return proofStorageService.completeDirectUpload(oidcUser.getSubject(), uploadId);
    }

    @GetMapping
    @Operation(summary = "List draft proof files", description = "Returns draft proof files for the authenticated player and selected challenge.")
    public ProofDraftResponse getDraftProofs(
            @RequestParam UUID challengeId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return proofStorageService.getDraftProofFiles(oidcUser.getSubject(), challengeId);
    }

    @PutMapping("/participants")
    @Operation(summary = "Save draft participants", description = "Stores the teammates chosen for a group challenge so they survive a page reload.")
    public ProofDraftResponse saveDraftParticipants(
            @RequestParam UUID challengeId,
            @RequestBody ProofDraftParticipantsRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return proofStorageService.saveDraftParticipants(
                oidcUser.getSubject(),
                challengeId,
                request == null ? null : request.participantUserIds()
        );
    }

    @DeleteMapping("/{uploadId}")
    @Operation(summary = "Delete a draft proof file", description = "Deletes an unfinished or unused draft proof file owned by the authenticated player.")
    public void deleteDraftProof(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        proofStorageService.deleteDraftProofFile(oidcUser.getSubject(), uploadId);
    }

}

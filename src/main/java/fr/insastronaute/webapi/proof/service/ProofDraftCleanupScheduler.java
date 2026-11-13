package fr.insastronaute.webapi.proof.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        name = "app.proofs.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProofDraftCleanupScheduler {

    private final ProofStorageService proofStorageService;
    private final Duration retention;
    private final Duration uploadTimeout;

    public ProofDraftCleanupScheduler(
            ProofStorageService proofStorageService,
            @Value("${app.proofs.draft-retention-hours}") long draftRetentionHours,
            @Value("${app.proofs.upload-timeout-minutes}") long uploadTimeoutMinutes
    ) {
        this.proofStorageService = proofStorageService;
        this.retention = Duration.ofHours(draftRetentionHours);
        this.uploadTimeout = Duration.ofMinutes(uploadTimeoutMinutes);
    }

    @Scheduled(
            initialDelayString = "${app.proofs.cleanup-initial-delay-ms}",
            fixedDelayString = "${app.proofs.cleanup-delay-ms}"
    )
    public void deleteStaleDrafts() {
        proofStorageService.deleteExpiredUploadingProofFiles(uploadTimeout);
        proofStorageService.deleteStaleDrafts(retention);
    }
}

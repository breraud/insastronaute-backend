package fr.insastronaute.webapi.proof.repository;

import fr.insastronaute.webapi.proof.domain.ProofDraft;
import fr.insastronaute.webapi.proof.domain.ProofUpload;
import fr.insastronaute.webapi.proof.domain.ProofUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofUploadRepository extends JpaRepository<ProofUpload, UUID> {
    /** La ligne qui decrit un objet du stockage, s'il en reste une. */
    java.util.Optional<ProofUpload> findByObjectKey(String objectKey);

    List<ProofUpload> findByDraft(ProofDraft draft);
    List<ProofUpload> findByDraftAndStatus(ProofDraft draft, ProofUploadStatus status);
    List<ProofUpload> findByDraftAndIdInAndStatus(ProofDraft draft, List<UUID> ids, ProofUploadStatus status);
    List<ProofUpload> findByStatusAndCreatedAtBefore(ProofUploadStatus status, Instant createdAt);
    /** Les reservations d'un brouillon qu'aucun envoi n'a terminees a temps. */
    List<ProofUpload> findByDraftAndStatusAndCreatedAtBefore(
            ProofDraft draft, ProofUploadStatus status, Instant createdAt);
    long countByDraftIdAndStatusIn(UUID draftId, Collection<ProofUploadStatus> statuses);
    Optional<ProofUpload> findByIdAndUserIdAndStatus(UUID id, UUID userId, ProofUploadStatus status);
    Optional<ProofUpload> findByIdAndUserIdAndStatusIn(UUID id, UUID userId, Collection<ProofUploadStatus> statuses);
    Optional<ProofUpload> findByDraftAndObjectKeyAndStatus(ProofDraft draft, String objectKey, ProofUploadStatus status);
    List<ProofUpload> findByObjectKeyIn(Collection<String> objectKeys);

    @Modifying
    @Query("""
            update ProofUpload upload
            set upload.status = :status
            where upload.id = :uploadId
            """)
    int updateStatus(@Param("uploadId") UUID uploadId, @Param("status") ProofUploadStatus status);
}

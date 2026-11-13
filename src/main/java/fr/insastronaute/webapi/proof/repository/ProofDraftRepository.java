package fr.insastronaute.webapi.proof.repository;

import fr.insastronaute.webapi.proof.domain.ProofDraft;
import fr.insastronaute.webapi.proof.domain.ProofDraftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofDraftRepository extends JpaRepository<ProofDraft, UUID> {
    Optional<ProofDraft> findByUserIdAndChallengeIdAndStatus(UUID userId, UUID challengeId, ProofDraftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select draft
            from ProofDraft draft
            where draft.user.id = :userId
              and draft.challenge.id = :challengeId
              and draft.status = :status
            """)
    Optional<ProofDraft> findByUserIdAndChallengeIdAndStatusForUpdate(
            @Param("userId") UUID userId,
            @Param("challengeId") UUID challengeId,
            @Param("status") ProofDraftStatus status
    );

    List<ProofDraft> findByStatusAndCreatedAtBefore(ProofDraftStatus status, Instant createdAt);

    @Modifying
    @Query("""
            update ProofDraft draft
            set draft.proofType = null
            where draft.id = :draftId
            """)
    int clearProofType(@Param("draftId") UUID draftId);
}

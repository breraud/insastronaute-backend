package fr.insastronaute.webapi.proof.domain;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proof_uploads")
public class ProofUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    private ProofDraft draft;

    @Column(name = "object_key", nullable = false, unique = true, columnDefinition = "TEXT")
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 20)
    private ProofType proofType;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProofUploadStatus status = ProofUploadStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public ProofDraft getDraft() { return draft; }
    public void setDraft(ProofDraft draft) { this.draft = draft; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public ProofType getProofType() { return proofType; }
    public void setProofType(ProofType proofType) { this.proofType = proofType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public ProofUploadStatus getStatus() { return status; }
    public void setStatus(ProofUploadStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

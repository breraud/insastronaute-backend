package fr.insastronaute.webapi.challenge.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "challenge_submissions")
public class ChallengeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "challenge_submission_participants",
            joinColumns = @JoinColumn(name = "submission_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<AppUser> participants = new HashSet<>();

    @Column(nullable = false, length = 20)
    private String status; // 'PENDING', 'VALIDATED', 'REJECTED'

    @Column(name = "proof_type", nullable = false)
    private String proofType; // TEXT, IMAGE, VIDEO

    @Column(name = "proof_data", columnDefinition = "TEXT", nullable = false)
    private String proofData; // Texte libre ou clé S3 pour les fichiers

    /**
     * Quand les fichiers ont ete effaces du stockage, apres arbitrage sur un defi sensible.
     *
     * <p>Conservee parce que la soumission, elle, reste : sans cette date, une preuve
     * purgee serait indistinguable d'une preuve qui n'a jamais eu de fichier, et l'ecran
     * afficherait « aucun fichier » la ou il faut dire ce qui s'est passe.</p>
     */
    @Column(name = "proof_media_purged_at")
    private java.time.Instant proofMediaPurgedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "moderation_comment", columnDefinition = "TEXT")
    private String moderationComment;

    @Column(name = "review_locked_by", length = 255)
    private String reviewLockedBy;

    @Column(name = "review_started_at")
    private Instant reviewStartedAt;

    @Column(name = "review_expires_at")
    private Instant reviewExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "bonus_points", nullable = false)
    private Integer bonusPoints = 0;

    /**
     * Declaree sensible par son auteur.
     *
     * <p>Le defi sensible existe deja, decide par l'organisation pour tout le monde.
     * Celui qui envoie une photo, lui, est seul a savoir qu'elle le devient dans son
     * cas : meme mecanique, a l'echelle d'une preuve.</p>
     */
    @Column(nullable = false)
    private Boolean sensitive = false;

    @Column(name = "messages_enabled", nullable = false)
    private Boolean messagesEnabled = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    // Getters et Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Set<AppUser> getParticipants() { return participants; }
    public void setParticipants(Set<AppUser> participants) {
        this.participants = participants == null ? new HashSet<>() : new HashSet<>(participants);
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProofType() {return proofType;}
    public void setProofType(String proofType) {this.proofType = proofType;}
    public String getProofData() {return proofData;}
    public void setProofData(String proofData) {this.proofData = proofData;}
    public Boolean getSensitive() { return sensitive; }
    public void setSensitive(Boolean sensitive) { this.sensitive = sensitive != null && sensitive; }
    public java.time.Instant getProofMediaPurgedAt() {return proofMediaPurgedAt;}
    public void setProofMediaPurgedAt(java.time.Instant proofMediaPurgedAt) {this.proofMediaPurgedAt = proofMediaPurgedAt;}
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getModerationComment() { return moderationComment; }
    public void setModerationComment(String moderationComment) { this.moderationComment = moderationComment; }
    public String getReviewLockedBy() { return reviewLockedBy; }
    public void setReviewLockedBy(String reviewLockedBy) { this.reviewLockedBy = reviewLockedBy; }
    public Instant getReviewStartedAt() { return reviewStartedAt; }
    public void setReviewStartedAt(Instant reviewStartedAt) { this.reviewStartedAt = reviewStartedAt; }
    public Instant getReviewExpiresAt() { return reviewExpiresAt; }
    public void setReviewExpiresAt(Instant reviewExpiresAt) { this.reviewExpiresAt = reviewExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
    public Integer getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(Integer bonusPoints) { this.bonusPoints = bonusPoints == null ? 0 : bonusPoints; }
    public Boolean getMessagesEnabled() { return messagesEnabled; }
    public void setMessagesEnabled(Boolean messagesEnabled) {
        this.messagesEnabled = messagesEnabled != null && messagesEnabled;
    }
}

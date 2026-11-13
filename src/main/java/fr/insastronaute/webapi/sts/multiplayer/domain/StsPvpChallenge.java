package fr.insastronaute.webapi.sts.multiplayer.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sts_pvp_challenges")
public class StsPvpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenger_id", nullable = false)
    private AppUser challenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenged_id", nullable = false)
    private AppUser challenged;

    @Column(name = "friendly", nullable = false)
    private Boolean friendly = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StsPvpChallengeStatus status = StsPvpChallengeStatus.PENDING;

    @Column(name = "battle_id")
    private UUID battleId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getChallenger() { return challenger; }
    public void setChallenger(AppUser challenger) { this.challenger = challenger; }
    public AppUser getChallenged() { return challenged; }
    public void setChallenged(AppUser challenged) { this.challenged = challenged; }
    public Boolean getFriendly() { return friendly; }
    public void setFriendly(Boolean friendly) { this.friendly = friendly; }
    public StsPvpChallengeStatus getStatus() { return status; }
    public void setStatus(StsPvpChallengeStatus status) { this.status = status; }
    public UUID getBattleId() { return battleId; }
    public void setBattleId(UUID battleId) { this.battleId = battleId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

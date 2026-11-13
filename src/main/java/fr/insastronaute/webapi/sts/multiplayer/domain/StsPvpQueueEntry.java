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
@Table(name = "sts_pvp_matchmaking_queue")
public class StsPvpQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "elo_snapshot", nullable = false)
    private Integer eloSnapshot;

    @Column(name = "friendly", nullable = false)
    private Boolean friendly;

    /** Le mode cherché. On ne s'apparie qu'entre entrées qui cherchent le même. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private StsPvpMode mode = StsPvpMode.ONE_V_ONE;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        queuedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Integer getEloSnapshot() { return eloSnapshot; }
    public void setEloSnapshot(Integer eloSnapshot) { this.eloSnapshot = eloSnapshot; }
    public Boolean getFriendly() { return friendly; }
    public void setFriendly(Boolean friendly) { this.friendly = friendly; }
    public StsPvpMode getMode() { return StsPvpMode.ofNullable(mode); }
    public void setMode(StsPvpMode mode) { this.mode = mode; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = Instant.now(); }
}

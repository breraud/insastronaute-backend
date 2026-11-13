package fr.insastronaute.webapi.leaderboard.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leaderboard_snapshots")
public class LeaderboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private LeaderboardConfig config;

    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    @Column(name = "eligibility_fingerprint", length = 64, updatable = false)
    private String eligibilityFingerprint;

    @PrePersist
    protected void onCreate() {
        if (this.releasedAt == null) {
            this.releasedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LeaderboardConfig getConfig() { return config; }
    public void setConfig(LeaderboardConfig config) { this.config = config; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public String getEligibilityFingerprint() { return eligibilityFingerprint; }
    public void setEligibilityFingerprint(String eligibilityFingerprint) {
        this.eligibilityFingerprint = eligibilityFingerprint;
    }
}

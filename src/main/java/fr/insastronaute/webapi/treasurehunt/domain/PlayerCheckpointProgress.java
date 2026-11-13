package fr.insastronaute.webapi.treasurehunt.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "player_checkpoint_progress")
public class PlayerCheckpointProgress {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @SuppressWarnings("unused")
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "checkpoint_id", nullable = false) private TreasureHuntCheckpoint checkpoint;
    @SuppressWarnings("unused")
    @jakarta.persistence.Column(name = "validated_at", nullable = false, updatable = false) private Instant validatedAt;
    @PrePersist protected void onCreate() { validatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public AppUser getUser() { return user; } public void setUser(AppUser user) { this.user = user; }
    public void setCheckpoint(TreasureHuntCheckpoint checkpoint) { this.checkpoint = checkpoint; }
}

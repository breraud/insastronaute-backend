package fr.insastronaute.webapi.treasurehunt.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "player_vault_unlocks")
public class PlayerVaultUnlock {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true) private AppUser user;
    @SuppressWarnings("unused")
    @Column(name = "unlocked_at", nullable = false, updatable = false) private Instant unlockedAt;
    @PrePersist protected void onCreate() { unlockedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public AppUser getUser() { return user; } public void setUser(AppUser user) { this.user = user; }
}

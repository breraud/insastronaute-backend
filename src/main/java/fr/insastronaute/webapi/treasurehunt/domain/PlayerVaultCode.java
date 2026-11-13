package fr.insastronaute.webapi.treasurehunt.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "player_vault_codes")
public class PlayerVaultCode {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "path_id", nullable = false) private TreasureHuntPath path;
    @Column(nullable = false, unique = true, length = 6) private String code;
    @SuppressWarnings("unused")
    @Column(name = "issued_at", nullable = false, updatable = false) private Instant issuedAt;
    @PrePersist protected void onCreate() { issuedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public AppUser getUser() { return user; } public void setUser(AppUser user) { this.user = user; }
    public TreasureHuntPath getPath() { return path; } public void setPath(TreasureHuntPath path) { this.path = path; }
    public String getCode() { return code; } public void setCode(String code) { this.code = code; }
}

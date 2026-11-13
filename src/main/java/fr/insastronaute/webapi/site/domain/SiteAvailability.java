package fr.insastronaute.webapi.site.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "site_availability")
public class SiteAvailability {

    public static final short SINGLETON_ID = 1;

    @Id
    @SuppressWarnings("FieldCanBeLocal") // Read by JPA through field access.
    private Short id = SINGLETON_ID;

    @Column(name = "player_access_enabled", nullable = false)
    private boolean playerAccessEnabled = true;

    @Column(name = "message")
    private String message;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private AppUser updatedBy;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = TimeTravelContext.instantNow();
    }

    public boolean isPlayerAccessEnabled() { return playerAccessEnabled; }
    public void setPlayerAccessEnabled(boolean playerAccessEnabled) { this.playerAccessEnabled = playerAccessEnabled; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AppUser getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(AppUser updatedBy) { this.updatedBy = updatedBy; }
}

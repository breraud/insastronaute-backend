package fr.insastronaute.webapi.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * L'interrupteur qui retire tous les défis du front joueur, en une ligne.
 *
 * Masqué, la page des défis est vide et rien ne peut être soumis. Aucun défi
 * n'est modifié pour autant : rallumer les rend tous tels qu'ils étaient, sans
 * avoir à se souvenir de ceux qu'on avait éteints un par un.
 */
@Entity
@Table(name = "challenge_visibility_settings")
public class ChallengeVisibilitySettings {

    @Id
    @SuppressWarnings("FieldCanBeLocal")
    private Boolean id = true;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public Boolean getId() { return id; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

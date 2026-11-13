package fr.insastronaute.webapi.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Les reglages qui valent pour tous les evenements.
 *
 * <p>Un seul enregistrement, tenu par une cle primaire booleenne contrainte a
 * {@code TRUE} : il ne peut pas en exister deux, donc aucun code n'a jamais a departager
 * lequel fait foi.</p>
 */
@Entity
@Table(name = "event_settings")
public class EventSettings {

    @Id
    private Boolean id = true;

    /**
     * Le staff peut-il encore prendre une place sur un evenement ?
     *
     * <p>Il joue sur le meme site que les etudiants, et se trouve souvent etre celui qui
     * organise. Fermer la porte ne modifie aucun evenement : la rouvrir rend exactement
     * ce qu'il y avait.</p>
     */
    @Column(name = "staff_registration_blocked", nullable = false)
    private boolean staffRegistrationBlocked;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    public Boolean getId() { return id; }
    public void setId(Boolean id) { this.id = id; }

    public boolean isStaffRegistrationBlocked() { return staffRegistrationBlocked; }
    public void setStaffRegistrationBlocked(boolean blocked) { this.staffRegistrationBlocked = blocked; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

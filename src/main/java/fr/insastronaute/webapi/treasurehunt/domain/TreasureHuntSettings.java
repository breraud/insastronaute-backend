package fr.insastronaute.webapi.treasurehunt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "treasure_hunt_settings")
public class TreasureHuntSettings {
    @Id
    @SuppressWarnings("FieldCanBeLocal")
    private Boolean id = true;
    private boolean active;
    /** Retire entièrement la chasse du front joueur, activation ou pas. */
    private boolean hidden;
    public Boolean getId() { return id; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}

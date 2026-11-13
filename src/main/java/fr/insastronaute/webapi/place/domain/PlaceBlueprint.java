package fr.insastronaute.webapi.place.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un plan de fresque : un dessin fait a cote, pose ensuite sur le vrai canevas.
 *
 * <p>Poser un pixel coute un delai, et une fresque a plusieurs mains se decide
 * avant de se poser. Ce travail se faisait ailleurs — un tableur, une capture
 * annotee — et se traduisait case par case, de tete, au moment de poser.</p>
 *
 * <p>Le dessin tient en palette et indices plutot qu'en couleurs repetees : une
 * case ne coute qu'un caractere, et un plan de 128x128 tient en seize
 * kilo-octets au lieu de cent. Voir {@link fr.insastronaute.webapi.place.service.BlueprintCells}.</p>
 */
@Entity
@Table(name = "place_blueprints")
public class PlaceBlueprint {

    public static final int MAX_SIDE = 128;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    /** Les couleurs employees, en #RRGGBB, separees par des virgules. */
    @Column(name = "palette", nullable = false, columnDefinition = "text")
    private String palette;

    /** Une case par caractere, ligne par ligne. Voir BlueprintCells. */
    @Column(name = "cells", nullable = false, columnDefinition = "text")
    private String cells;

    /**
     * Ou l'auteur a pose le plan sur la fresque, ou null s'il ne l'a pas pose.
     *
     * <p>Portee par le plan et non par la page : c'est ce qui la fait arriver
     * chez les amis avec qui on le partage.</p>
     */
    @Column(name = "anchor_x")
    private Integer anchorX;

    @Column(name = "anchor_y")
    private Integer anchorY;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public String getPalette() { return palette; }
    public void setPalette(String palette) { this.palette = palette; }
    public String getCells() { return cells; }
    public void setCells(String cells) { this.cells = cells; }
    public Integer getAnchorX() { return anchorX; }
    public Integer getAnchorY() { return anchorY; }
    public void setAnchor(int x, int y) { this.anchorX = x; this.anchorY = y; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

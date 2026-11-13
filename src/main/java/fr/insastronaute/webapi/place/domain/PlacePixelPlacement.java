package fr.insastronaute.webapi.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un pixel posé, et par qui.
 *
 * <p>Le canevas lui-même vit dans Redis en octets RGB : il dit ce qui s'affiche,
 * jamais qui l'a mis. Cette table est la seule trace qui permette d'imputer une
 * image choquante à quelqu'un plutôt que de se contenter de la recouvrir.</p>
 *
 * <p>Elle est en ajout seul : chaque pose s'ajoute, aucune ne remplace la
 * précédente. Sinon il suffirait de repasser sur son propre pixel pour effacer
 * la trace de ce qu'on y avait dessiné.</p>
 */
@Entity
@Table(name = "place_pixel_placements")
public class PlacePixelPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String generation;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected PlacePixelPlacement() {
    }

    public PlacePixelPlacement(String generation, int x, int y, String color, UUID userId, Instant placedAt) {
        this.generation = generation;
        this.x = x;
        this.y = y;
        this.color = color;
        this.userId = userId;
        this.placedAt = placedAt;
    }

    public Long getId() { return id; }
    public String getGeneration() { return generation; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getColor() { return color; }
    public UUID getUserId() { return userId; }
    public Instant getPlacedAt() { return placedAt; }
}

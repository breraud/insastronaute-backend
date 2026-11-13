package fr.insastronaute.webapi.deck.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decks")
public class Deck {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(name = "cards_per_pull", nullable = false)
    private Integer cardsPerPull = 1;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeckKind kind = DeckKind.PACK;

    /**
     * Où se trouve le recto dans la largeur de l'emballage à plat, en pourcentage.
     *
     * <p>L'image d'un pack montre l'emballage déplié : le recto au centre, le verso
     * réparti sur les bords. 50 vise le centre, ce qui suffit à un emballage
     * symétrique ; un autre chiffre rattrape une illustration décentrée.</p>
     */
    @Column(name = "front_x_percent", nullable = false)
    private Double frontXPercent = 50.0;

    /** De combien grossir pour que le recto remplisse le cadre. 1 = recadrage simple. */
    @Column(name = "front_zoom", nullable = false)
    private Double frontZoom = 1.0;

    @OrderBy("displayOrder ASC")
    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DropTier> dropTiers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public void addDropTier(DropTier dropTier) {
        dropTier.setDeck(this);
        dropTiers.add(dropTier);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }
    public Integer getCardsPerPull() { return cardsPerPull; }
    public void setCardsPerPull(Integer cardsPerPull) {
        this.cardsPerPull = cardsPerPull == null || cardsPerPull < 1 ? 1 : cardsPerPull;
    }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Double getFrontXPercent() { return frontXPercent; }
    public void setFrontXPercent(Double frontXPercent) {
        this.frontXPercent = frontXPercent == null ? 50.0 : frontXPercent;
    }

    public Double getFrontZoom() { return frontZoom; }
    public void setFrontZoom(Double frontZoom) {
        this.frontZoom = frontZoom == null ? 1.0 : frontZoom;
    }

    public DeckKind getKind() { return kind; }
    public void setKind(DeckKind kind) { this.kind = kind == null ? DeckKind.PACK : kind; }
    public List<DropTier> getDropTiers() { return dropTiers; }
    public void setDropTiers(List<DropTier> dropTiers) {
        this.dropTiers.clear();
        if (dropTiers != null) {
            dropTiers.forEach(this::addDropTier);
        }
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

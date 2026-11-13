package fr.insastronaute.webapi.deck.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A tier arrangement kept aside so another pack can start from it.
 *
 * It carries its own {@code cardsPerPull} because the tier rules only make sense
 * against one: a template guaranteeing three cards is valid in a five-card pack
 * and impossible in a two-card one, and the number it was built for is the only
 * one that proves it was ever coherent.
 */
@Entity
@Table(name = "deck_tier_templates")
public class DropTierTemplate {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "cards_per_pull", nullable = false)
    private Integer cardsPerPull = 1;

    @OrderBy("displayOrder ASC")
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DropTierTemplateTier> tiers = new ArrayList<>();

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
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public void addTier(DropTierTemplateTier tier) {
        tier.setTemplate(this);
        tiers.add(tier);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCardsPerPull() { return cardsPerPull; }
    public void setCardsPerPull(Integer cardsPerPull) {
        this.cardsPerPull = cardsPerPull == null || cardsPerPull < 1 ? 1 : cardsPerPull;
    }
    public List<DropTierTemplateTier> getTiers() { return tiers; }
    public void setTiers(List<DropTierTemplateTier> tiers) {
        this.tiers.clear();
        if (tiers != null) {
            tiers.forEach(this::addTier);
        }
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

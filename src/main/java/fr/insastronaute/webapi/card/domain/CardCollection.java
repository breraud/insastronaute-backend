package fr.insastronaute.webapi.card.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "card_collections",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_card_collections_user_card_type",
                columnNames = {"user_id", "card_id", "collection_type"}
        )
)
public class CardCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false, length = 20)
    private CardCollectionType collectionType = CardCollectionType.VIRTUAL;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "first_discovered_at", nullable = false, updatable = false)
    private Instant firstDiscoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.firstDiscoveredAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.updatedAt = this.firstDiscoveredAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }
    public CardCollectionType getCollectionType() { return collectionType; }
    public void setCollectionType(CardCollectionType collectionType) {
        this.collectionType = collectionType == null ? CardCollectionType.VIRTUAL : collectionType;
    }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity == null || quantity < 1 ? 1 : quantity; }
    public Instant getFirstDiscoveredAt() { return firstDiscoveredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

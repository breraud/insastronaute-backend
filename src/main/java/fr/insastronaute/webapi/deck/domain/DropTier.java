package fr.insastronaute.webapi.deck.domain;

import fr.insastronaute.webapi.card.domain.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deck_drop_tiers")
public class DropTier {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(length = 120)
    private String label;

    @Column(name = "drop_chance_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal dropChancePercent;

    @Column(name = "max_cards_per_pull")
    private Integer maxCardsPerPull;

    @Column(name = "min_cards_per_pull")
    private Integer minCardsPerPull;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OrderColumn(name = "card_order")
    @ManyToMany
    @JoinTable(
            name = "deck_drop_tier_cards",
            joinColumns = @JoinColumn(name = "drop_tier_id"),
            inverseJoinColumns = @JoinColumn(name = "card_id")
    )
    private List<Card> cards = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Deck getDeck() { return deck; }
    public void setDeck(Deck deck) { this.deck = deck; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getDropChancePercent() { return dropChancePercent; }
    public void setDropChancePercent(BigDecimal dropChancePercent) { this.dropChancePercent = dropChancePercent; }
    public Integer getMaxCardsPerPull() { return maxCardsPerPull; }
    public void setMaxCardsPerPull(Integer maxCardsPerPull) { this.maxCardsPerPull = maxCardsPerPull; }
    public Integer getMinCardsPerPull() { return minCardsPerPull; }
    public void setMinCardsPerPull(Integer minCardsPerPull) {
        this.minCardsPerPull = minCardsPerPull == null || minCardsPerPull < 1 ? null : minCardsPerPull;
    }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder == null ? 0 : displayOrder; }
    public List<Card> getCards() { return cards; }
    public void setCards(List<Card> cards) {
        this.cards.clear();
        if (cards != null) {
            this.cards.addAll(cards);
        }
    }
}

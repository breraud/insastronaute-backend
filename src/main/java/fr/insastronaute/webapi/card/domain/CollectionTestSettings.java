package fr.insastronaute.webapi.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Le mode de test des collections, en une ligne.
 *
 * Levé, chaque joueur voit et utilise la collection complète : de quoi essayer
 * ce que les cartes débloquent — decks PvP en tête — sans avoir à les
 * distribuer. Rien n'est donné pour autant : les collections réelles ne bougent
 * pas, et ce sont elles qui comptent pour le classement, les succès et les
 * points de collection.
 */
@Entity
@Table(name = "collection_test_settings")
public class CollectionTestSettings {

    @Id
    @SuppressWarnings("FieldCanBeLocal")
    private Boolean id = true;

    @Column(name = "all_cards_owned", nullable = false)
    private boolean allCardsOwned;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public Boolean getId() { return id; }
    public boolean isAllCardsOwned() { return allCardsOwned; }
    public void setAllCardsOwned(boolean allCardsOwned) { this.allCardsOwned = allCardsOwned; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package fr.insastronaute.webapi.card.goofy;

import fr.insastronaute.webapi.card.domain.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Le code cache et la carte qu'il donne, en un seul exemplaire.
 *
 * <p>Singleton, comme les autres réglages de collection : il n'y a qu'une carte
 * goofy à la fois, et une table à une ligne se lit sans se demander laquelle des
 * lignes fait foi.</p>
 */
@Entity
@Table(name = "goofy_card_settings")
public class GoofyCardSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @Column(length = 64)
    private String code;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

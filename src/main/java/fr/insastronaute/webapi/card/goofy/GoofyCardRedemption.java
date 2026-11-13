package fr.insastronaute.webapi.card.goofy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Qui a déjà eu la carte goofy. L'identifiant est la clé : une fois par personne. */
@Entity
@Table(name = "goofy_card_redemptions")
public class GoofyCardRedemption {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    protected GoofyCardRedemption() {
    }

    public GoofyCardRedemption(UUID userId, Instant redeemedAt) {
        this.userId = userId;
        this.redeemedAt = redeemedAt;
    }

    public UUID getUserId() { return userId; }
    public Instant getRedeemedAt() { return redeemedAt; }
}

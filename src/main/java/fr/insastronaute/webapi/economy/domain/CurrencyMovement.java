package fr.insastronaute.webapi.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du registre : ce qui a bouge, pour qui, et pourquoi.
 *
 * <p>Identifiants plutot qu'associations : le registre s'ecrit au milieu de
 * gestes qui tiennent deja le joueur verrouille, et se relit par milliers pour
 * l'onglet stats — il n'a jamais besoin de charger l'entite joueur.</p>
 */
@Entity
@Table(name = "currency_movements")
public class CurrencyMovement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private CurrencySource source;

    @Column(name = "token_delta", nullable = false)
    private long tokenDelta;

    @Column(name = "event_points_delta", nullable = false)
    private long eventPointsDelta;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CurrencyMovement() {
    }

    public CurrencyMovement(UUID userId, CurrencySource source, long tokenDelta, long eventPointsDelta,
                            UUID referenceId, Instant occurredAt) {
        this.userId = userId;
        this.source = source;
        this.tokenDelta = tokenDelta;
        this.eventPointsDelta = eventPointsDelta;
        this.referenceId = referenceId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public CurrencySource getSource() { return source; }
    public long getTokenDelta() { return tokenDelta; }
    public long getEventPointsDelta() { return eventPointsDelta; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getOccurredAt() { return occurredAt; }
}

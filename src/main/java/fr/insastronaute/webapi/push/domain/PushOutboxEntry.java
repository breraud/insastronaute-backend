package fr.insastronaute.webapi.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un envoi a faire.
 *
 * <p>Deposee dans la transaction qui ecrit la notification, reprise par un
 * planificateur. C'est ce detour qui tient la seule regle non negociable du push :
 * <strong>un service injoignable ne doit jamais faire echouer l'ecriture</strong>. La
 * transaction qui accorde un succes ou valide un defi ne touche pas au reseau ; elle
 * pose une ligne.</p>
 *
 * <p>C'est aussi ce qui protege les diffusions. Annuler un evenement previent tous ses
 * inscrits : sans file, deux cents envois HTTPS partiraient en serie sur le fil de la
 * requete de l'administrateur.</p>
 *
 * <p>La ligne porte l'identifiant de la notification et rien d'autre. Les abonnements
 * sont resolus au moment de l'envoi, donc un appareil abonne entre-temps recoit, et un
 * appareil disparu ne laisse pas de ligne morte.</p>
 */
@Entity
@Table(name = "push_outbox")
public class PushOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    protected PushOutboxEntry() {}

    public PushOutboxEntry(UUID notificationId) {
        this.notificationId = notificationId;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }

    public void incrementAttempts() {
        this.attempts++;
    }
}

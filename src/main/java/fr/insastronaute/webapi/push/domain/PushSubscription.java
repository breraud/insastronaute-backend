package fr.insastronaute.webapi.push.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un appareil qui accepte de recevoir les notifications, application fermee.
 *
 * <p>Un abonnement decrit un appareil et pas une personne : le meme etudiant sur son
 * telephone et sur son portable en a deux, et l'unicite porte donc sur
 * {@code endpoint}. Se reabonner depuis le meme navigateur remplace l'ancien plutot
 * que d'en empiler un second - sans quoi chaque vidage de cache laisserait derriere
 * lui un abonnement mort que chaque envoi paierait.</p>
 *
 * <p>{@code p256dh} et {@code auth} sont les cles que le navigateur a fabriquees : le
 * serveur chiffre pour elles et ne peut pas relire ce qu'il a envoye. Elles ne valent
 * rien sans l'endpoint, mais elles restent des secrets d'appareil - rien ne les
 * expose dans une reponse d'API.</p>
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String auth;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Le dernier envoi accepte. Il ne sert pas a purger - c'est 404/410 qui purge - mais a voir. */
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    protected PushSubscription() {}

    public PushSubscription(AppUser user, String endpoint, String p256dh, String auth, String userAgent) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }

    public void refreshKeys(String p256dh, String auth, String userAgent) {
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }

    public void markDelivered(Instant at) {
        this.lastSuccessAt = at;
    }
}

package fr.insastronaute.webapi.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Un exemplaire physique a deja paye ce joueur pour cette carte.
 *
 * <p>Depuis qu'un joueur peut liberer un doublon, un meme exemplaire peut repasser
 * plusieurs fois entre les memes mains : rescanne par celui qui l'a libere, ou
 * renvoye d'un joueur a l'autre. La collection suit la carte a chaque passage ;
 * les jetons, eux, ne se touchent qu'une fois par exemplaire et par joueur. La carte
 * fait partie de la cle parce qu'un exemplaire repointe par l'administration vers une
 * autre carte est, pour son proprietaire, une acquisition nouvelle.</p>
 */
@Entity
@Table(
        name = "card_instance_rewards",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_card_instance_rewards_instance_user_card",
                columnNames = {"card_instance_id", "user_id", "card_id"}
        )
)
public class CardInstanceReward {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_instance_id", nullable = false)
    private UUID cardInstanceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "rewarded_at", nullable = false, updatable = false)
    private Instant rewardedAt;

    protected CardInstanceReward() {
    }

    public CardInstanceReward(UUID cardInstanceId, UUID userId, UUID cardId) {
        this.cardInstanceId = cardInstanceId;
        this.userId = userId;
        this.cardId = cardId;
    }

    @PrePersist
    protected void onCreate() {
        this.rewardedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public UUID getCardInstanceId() { return cardInstanceId; }
    public UUID getUserId() { return userId; }
    public UUID getCardId() { return cardId; }
    public Instant getRewardedAt() { return rewardedAt; }
}

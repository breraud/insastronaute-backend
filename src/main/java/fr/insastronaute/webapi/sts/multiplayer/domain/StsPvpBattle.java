package fr.insastronaute.webapi.sts.multiplayer.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sts_pvp_battles")
public class StsPvpBattle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StsPvpBattleStatus status = StsPvpBattleStatus.PENDING;

    @Column(name = "friendly", nullable = false)
    private Boolean friendly = false;

    /** Ce que cette bataille oppose. Voir {@link StsPvpMode}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private StsPvpMode mode = StsPvpMode.ONE_V_ONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teams", nullable = false, columnDefinition = "jsonb")
    private JsonNode teams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", nullable = false, columnDefinition = "jsonb")
    private JsonNode state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "heartbeat", nullable = false, columnDefinition = "jsonb")
    private JsonNode heartbeat;

    /**
     * Les cartes jouées dans ce duel, dans l'ordre, telles que les deux joueurs les voient.
     *
     * <p>Stocké plutôt que reconstitué par chaque client : un historique accumulé sur le poste
     * du joueur repart vide à la moindre reconnexion, et deux joueurs qui ne voient pas la même
     * chose n'ont pas d'historique partagé. Voir {@code StsPvpCardHistory}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_history", nullable = false, columnDefinition = "jsonb")
    private JsonNode cardHistory;

    /**
     * Ce que ce duel a rapporté ou coûté à chacun, une fois réglé.
     *
     * <p>Gardé plutôt que recalculé : la formule part des classements d'avant le duel, et ceux-là
     * ont déjà bougé. Voir {@code StsPvpBattleRewards}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rewards", nullable = false, columnDefinition = "jsonb")
    private JsonNode rewards;

    @Column(name = "turn_deadline")
    private Instant turnDeadline;

    @Column(name = "winner_team")
    private Integer winnerTeam;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public StsPvpBattleStatus getStatus() { return status; }
    public void setStatus(StsPvpBattleStatus status) { this.status = status; }
    public Boolean getFriendly() { return friendly; }
    public void setFriendly(Boolean friendly) { this.friendly = friendly; }
    public StsPvpMode getMode() { return StsPvpMode.ofNullable(mode); }
    public void setMode(StsPvpMode mode) { this.mode = mode; }
    public JsonNode getTeams() { return teams; }
    public void setTeams(JsonNode teams) { this.teams = teams; }
    public JsonNode getState() { return state; }
    public void setState(JsonNode state) { this.state = state; }
    public JsonNode getHeartbeat() { return heartbeat; }
    public void setHeartbeat(JsonNode heartbeat) { this.heartbeat = heartbeat; }
    public JsonNode getCardHistory() { return cardHistory; }
    public void setCardHistory(JsonNode cardHistory) { this.cardHistory = cardHistory; }
    public JsonNode getRewards() { return rewards; }
    public void setRewards(JsonNode rewards) { this.rewards = rewards; }
    public Instant getTurnDeadline() { return turnDeadline; }
    public void setTurnDeadline(Instant turnDeadline) { this.turnDeadline = turnDeadline; }
    public Integer getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(Integer winnerTeam) { this.winnerTeam = winnerTeam; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

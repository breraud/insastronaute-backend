package fr.insastronaute.webapi.sts.multiplayer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One command a PvP battle has already resolved.
 *
 * <p>A STOMP frame can be delivered twice, and resolving the same command twice would play the
 * card twice. Remembering what a battle has already accepted lets the second delivery be answered
 * with the state the first one produced instead of resolving it again.
 *
 * <p>The PvE ledger cannot serve here: its {@code run_id} points at {@code sts_runs}, and a PvP
 * battle is not a run. The key is also wider than PvE's: two <em>different</em> players sit in one
 * battle and each numbers its own actions, so the same {@code actionId} can legitimately arrive
 * from both. Keying on the acting player as well keeps one player's action from being mistaken for
 * a replay of the other's.
 *
 * <p>Only accepted commands are recorded. A refused command changed nothing, so re-evaluating it
 * cannot double-play anything — the danger this ledger exists for is a command being applied
 * twice, not a rejection being computed twice.
 */
@Entity
@Table(name = "sts_pvp_battle_commands")
public class StsPvpBattleCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "battle_id", nullable = false)
    private UUID battleId;

    @Column(name = "acting_user_id", nullable = false)
    private UUID actingUserId;

    @Column(name = "action_id", nullable = false, length = 128)
    private String actionId;

    @Column(name = "resulting_revision", nullable = false)
    private long resultingRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StsPvpBattleCommand() {
    }

    public static StsPvpBattleCommand accepted(
            UUID battleId,
            UUID actingUserId,
            String actionId,
            long resultingRevision
    ) {
        StsPvpBattleCommand command = new StsPvpBattleCommand();
        command.battleId = battleId;
        command.actingUserId = actingUserId;
        command.actionId = actionId;
        command.resultingRevision = resultingRevision;
        return command;
    }

    @PrePersist
    void stampCreation() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBattleId() {
        return battleId;
    }

    public UUID getActingUserId() {
        return actingUserId;
    }

    public String getActionId() {
        return actionId;
    }

    public long getResultingRevision() {
        return resultingRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package fr.insastronaute.webapi.sts.domain;

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
 * The outcome of one command a run has already answered.
 *
 * <p>A realtime frame can be delivered more than once, and resolving the same command twice would
 * play the card twice. Remembering what the first delivery produced lets the second be answered
 * with the same thing instead.
 */
@Entity
@Table(name = "sts_combat_commands")
public class StsCombatCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "action_id", nullable = false, length = 128)
    private String actionId;

    @Column(nullable = false)
    private boolean accepted;

    @Column(name = "resulting_revision")
    private Long resultingRevision;

    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;

    @Column(name = "rejection_message")
    private String rejectionMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StsCombatCommand() {
    }

    public static StsCombatCommand accepted(UUID runId, String actionId, long resultingRevision) {
        StsCombatCommand command = new StsCombatCommand();
        command.runId = runId;
        command.actionId = actionId;
        command.accepted = true;
        command.resultingRevision = resultingRevision;
        return command;
    }

    public static StsCombatCommand rejected(
            UUID runId,
            String actionId,
            String code,
            String message
    ) {
        StsCombatCommand command = new StsCombatCommand();
        command.runId = runId;
        command.actionId = actionId;
        command.accepted = false;
        command.rejectionCode = code;
        command.rejectionMessage = message;
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

    public UUID getRunId() {
        return runId;
    }

    public String getActionId() {
        return actionId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public Long getResultingRevision() {
        return resultingRevision;
    }

    public String getRejectionCode() {
        return rejectionCode;
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

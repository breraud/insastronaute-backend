package fr.insastronaute.webapi.sts.combat.command;

import java.util.UUID;

/**
 * Trusted internal domain command, never a transport DTO.
 *
 * <p>The orchestrator must derive the actor from the authenticated principal and the combat
 * assignment instead of copying an actor identifier from a network payload.
 */
public sealed interface CombatCommand permits PlayCardCommand, EndTurnCommand, AiPlayPatternCommand {

    UUID combatId();

    long expectedRevision();

    /**
     * Returns the trusted actor identifier established by the orchestrator.
     *
     * @return actor identifier derived from authenticated principal and combat assignment
     */
    String actorId();
}

package fr.insastronaute.webapi.sts.combat.api.dto;

public record CombatCommandEnvelope(
    int protocolVersion,
    String actionId,
    String combatId,
    String expectedRevision,
    String type,
    Object payload
) {}

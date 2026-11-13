package fr.insastronaute.webapi.sts.combat.api.dto;

public record CombatSnapshotDto(
    int protocolVersion,
    String combatId,
    String revision,
    String mode,
    Object payload
) {}

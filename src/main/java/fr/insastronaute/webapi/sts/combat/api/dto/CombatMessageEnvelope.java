package fr.insastronaute.webapi.sts.combat.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CombatMessageEnvelope(
    int protocolVersion,
    String combatId,
    String revision,
    String type,
    String causationActionId,
    Object payload
) {}

package fr.insastronaute.webapi.sts.dto;

import java.util.UUID;

public record StsCombatStateResponse(
        boolean accepted,
        UUID runId,
        Object combat
) {
}

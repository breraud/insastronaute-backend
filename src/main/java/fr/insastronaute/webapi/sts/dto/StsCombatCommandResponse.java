package fr.insastronaute.webapi.sts.dto;

import java.util.List;
import java.util.UUID;

public record StsCombatCommandResponse(
        boolean accepted,
        UUID runId,
        Object combat,
        List<Object> events,
        String rejectionCode,
        String rejectionMessage
) {
}

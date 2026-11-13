package fr.insastronaute.webapi.auth.gate.dto;

import fr.insastronaute.webapi.auth.gate.GateStatus;

public record GateStatusResponse(
        GateStatus status,
        String ticketId,
        String gateToken,
        String redirectUrl,
        Integer position,
        Long totalInQueue,
        Long estimatedWaitSeconds
) {}

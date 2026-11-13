package fr.insastronaute.webapi.sts.dto;

import java.util.UUID;

public record StsCompleteNodeResponse(
        boolean accepted,
        UUID runId,
        int currentNodeId,
        Object player,
        Object runInventoryPatch,
        Object accountInventoryPatch,
        Object pendingRewards,
        Object mapPatch
) {
}

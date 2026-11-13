package fr.insastronaute.webapi.sts.dto;

public record StsChooseEventOptionResponse(
        boolean accepted,
        String eventInstanceId,
        boolean eventCompleted,
        Object player,
        Object runInventoryPatch,
        Object accountInventoryPatch,
        Object pendingRewards,
        Object mapPatch,
        String completionMessage,
        /** L'événement à jour quand il continue ; null quand il vient de se clore. */
        Object activeEvent
) {
}

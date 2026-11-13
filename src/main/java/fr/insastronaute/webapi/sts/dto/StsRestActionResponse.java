package fr.insastronaute.webapi.sts.dto;

public record StsRestActionResponse(
        boolean accepted,
        String action,
        Object player,
        Object runInventoryPatch,
        int restCharges,
        int maxRestCharges
) {
}

package fr.insastronaute.webapi.sts.dto;

public record StsClaimRewardResponse(
        boolean accepted,
        Object player,
        Object runInventory,
        Object pendingRewards
) {
}

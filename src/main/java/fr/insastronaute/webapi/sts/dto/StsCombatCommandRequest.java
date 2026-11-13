package fr.insastronaute.webapi.sts.dto;

import java.util.List;

public record StsCombatCommandRequest(
        String commandType,
        Long expectedRevision,
        String cardInstanceId,
        List<String> targetIds,
        List<String> selectedCardInstanceIds
) {
    public StsCombatCommandRequest {
        targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
        selectedCardInstanceIds = selectedCardInstanceIds == null
                ? List.of()
                : List.copyOf(selectedCardInstanceIds);
    }
}

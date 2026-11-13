package fr.insastronaute.webapi.sts.dto;

import java.util.UUID;

public record StsDebugCombatResponse(
        boolean accepted,
        UUID runId,
        Object player,
        Object runInventory,
        Object activeEncounter,
        Object activeCombat
) {
}

package fr.insastronaute.webapi.sts.dto;

import java.util.UUID;

public record StsRunStateDto(
        UUID runId,
        String status,
        String dataVersion,
        String selectedCharacter,
        int act,
        int currentFloor,
        int currentNodeId,
        Integer enteredNodeId,
        Object player,
        Object runInventory,
        Object map,
        Object pendingRewards,
        Object activeEncounter,
        Object activeCombat,
        Object activeEvent,
        /** Si le compte a ouvert le pool de cartes secrètes : l'état serveur, pas une supposition du client. */
        boolean secretCardsUnlocked
) {
    /** La forme d'avant le drapeau secret, pour les appelants qui n'ont pas de compte sous la main. */
    public StsRunStateDto(
            UUID runId, String status, String dataVersion, String selectedCharacter,
            int act, int currentFloor, int currentNodeId, Integer enteredNodeId,
            Object player, Object runInventory, Object map, Object pendingRewards,
            Object activeEncounter, Object activeCombat, Object activeEvent
    ) {
        this(runId, status, dataVersion, selectedCharacter, act, currentFloor, currentNodeId,
                enteredNodeId, player, runInventory, map, pendingRewards, activeEncounter,
                activeCombat, activeEvent, false);
    }
}

package fr.insastronaute.webapi.sts.multiplayer.dto;


import java.util.Map;

public record StsPvpBattleActionRequest(
        String actionType,
        // Une table et non un JsonNode : la couche HTTP est en Jackson 3, qui ne
        // sait pas construire l'arbre de Jackson 2. Voir JsonPayloads.
        Map<String, Object> action,
        Boolean endTurn,
        Boolean heartbeatOnly
) {
}

package fr.insastronaute.webapi.sts.dto;

import java.util.UUID;

public record StsEnterNodeResponse(
        boolean accepted,
        UUID runId,
        int nodeId,
        String nodeType,
        Object activeEncounter,
        Object activeCombat,
        String eventId,
        Object activeEvent,
        /**
         * L'état du joueur à l'entrée du nœud.
         *
         * <p>Un feu de camp y pose les charges et y fait jouer les reliques de repos :
         * sans cet état dans la réponse, le client afficherait les charges avec
         * lesquelles il est arrivé et ignorerait le soin que les reliques viennent de
         * lui rendre.
         */
        Object player
) {
}

package fr.insastronaute.webapi.sts.dto;

/**
 * Ce que le client a besoin de savoir avant de proposer le menu de débogage.
 *
 * @param enabled   le serveur porte la route : {@code app.sts.debug.combat.enabled} est vrai
 * @param allowed   l'appelant a l'un des rôles auxquels la route est ouverte
 * @param available les deux à la fois, c'est-à-dire la seule question que pose le client
 */
public record StsDebugAvailabilityResponse(
        boolean enabled,
        boolean allowed,
        boolean available
) {
}

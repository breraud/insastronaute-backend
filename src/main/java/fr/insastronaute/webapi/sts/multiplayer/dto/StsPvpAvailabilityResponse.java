package fr.insastronaute.webapi.sts.multiplayer.dto;

/**
 * Si le mode PvP est ouvert sur ce serveur.
 *
 * @param enabled {@code app.sts.pvp.enabled} ; faux, aucune route PvP n'est déployée
 */
public record StsPvpAvailabilityResponse(boolean enabled) {
}

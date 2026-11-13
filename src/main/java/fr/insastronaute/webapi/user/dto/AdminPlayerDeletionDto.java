package fr.insastronaute.webapi.user.dto;

/**
 * Ce qu'une suppression de joueur a reellement fait.
 *
 * <p>Deux issues, et l'administrateur doit savoir laquelle : {@code purged} vaut vrai
 * quand la ligne a disparu pour de bon, faux quand elle survit parce que le registre
 * d'administration la retient -- il continue alors de nommer ce joueur.</p>
 */
public record AdminPlayerDeletionDto(
        String displayName,
        boolean purged,
        boolean keycloakAccountWasMissing,
        String retainedBecause
) {
}

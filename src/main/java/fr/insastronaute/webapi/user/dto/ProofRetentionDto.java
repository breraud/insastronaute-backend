package fr.insastronaute.webapi.user.dto;

/**
 * Ce que le joueur veut qu'il advienne de ses preuves une fois le défi validé.
 *
 * <p>Une preuve est une photo de soi, souvent avec d'autres, envoyée pour qu'un
 * modérateur la regarde. Cet examen fini, elle n'a plus d'usage — et jusqu'ici,
 * rien ne permettait de demander qu'elle parte.</p>
 */
public record ProofRetentionDto(boolean purgeAfterValidation) {}

package fr.insastronaute.webapi.sts.multiplayer.dto;

/**
 * L'etat d'une file d'attente, pour que le joueur sache s'il a une chance de trouver quelqu'un.
 *
 * @param mode     le mode interroge, tel que l'enum le nomme
 * @param friendly la file amicale ou la file classee : on ne s'apparie pas entre les deux
 * @param waiting  combien de joueurs y cherchent en ce moment, le demandeur compris s'il y est
 * @param required combien il en faut pour ouvrir un combat dans ce mode
 */
public record StsPvpQueueStatusResponse(
        String mode,
        boolean friendly,
        long waiting,
        int required
) {
}

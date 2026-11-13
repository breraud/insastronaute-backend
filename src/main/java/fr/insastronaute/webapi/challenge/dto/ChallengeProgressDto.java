package fr.insastronaute.webapi.challenge.dto;

/**
 * Où en est un joueur sur un défi à validation automatique.
 *
 * <p>Rien à envoyer sur ces défis : le seul retour possible est de dire où l'on
 * en est. Sans cela, la page n'a rien à montrer d'un défi automatique, et il
 * ressemble à un défi qui ne marche pas.</p>
 */
public record ChallengeProgressDto(long current, long target, int percentage, boolean completed) {
}

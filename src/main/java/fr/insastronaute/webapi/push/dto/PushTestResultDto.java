package fr.insastronaute.webapi.push.dto;

/**
 * Ce que l'envoi de test rend.
 *
 * <p>{@code subscribedDevices} vaut zero bien plus souvent qu'on ne le croit : le push
 * s'active dans l'application joueur, pas dans le panel. Le dire evite de conclure que
 * la chaine est cassee alors que personne n'ecoute.</p>
 */
public record PushTestResultDto(long subscribedDevices, String message) {
}

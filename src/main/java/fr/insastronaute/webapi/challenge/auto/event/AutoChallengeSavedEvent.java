package fr.insastronaute.webapi.challenge.auto.event;

import java.util.UUID;

/**
 * Une phrase automatique vient d'être écrite ou modifiée.
 *
 * <p>Publiée par l'administration, écoutée après le commit : le défi doit exister
 * en base avant qu'on cherche qui le remplit déjà.</p>
 */
public record AutoChallengeSavedEvent(UUID challengeId) {
}

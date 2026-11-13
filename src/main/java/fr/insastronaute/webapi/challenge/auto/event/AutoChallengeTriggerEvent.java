package fr.insastronaute.webapi.challenge.auto.event;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;

import java.util.UUID;

/** Un joueur vient de faire quelque chose qui peut satisfaire une phrase clé. */
public record AutoChallengeTriggerEvent(UUID userId, AutoChallengeTrigger trigger) {
}

package fr.insastronaute.webapi.push.dto;

import jakarta.validation.constraints.NotBlank;

/** Ce que {@code PushManager.subscribe()} rend au navigateur, tel quel. */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotBlank String p256dh,
        @NotBlank String auth
) {
}

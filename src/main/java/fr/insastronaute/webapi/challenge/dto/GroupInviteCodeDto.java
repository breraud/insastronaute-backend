package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Le code a montrer pour qu'on rejoigne son equipe.
 *
 * @param joinUrl ce que le QR encode : l'appareil photo l'ouvre, l'application prend le relais
 * @param qrSvg   le dessin du code, rendu par le serveur
 */
public record GroupInviteCodeDto(
        UUID token,
        String joinUrl,
        Instant expiresAt,
        String qrSvg
) {
}

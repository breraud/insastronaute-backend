package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Un défi entre amis, vu par l'un des deux.
 *
 * @param direction {@code INCOMING} pour un défi reçu, {@code OUTGOING} pour un défi lancé. Donné
 *                  par le serveur plutôt que déduit par le client en comparant des identifiants :
 *                  le jeu et le site n'ont pas toujours l'identifiant du joueur sous la main.
 * @param battleId  la bataille ouverte par l'acceptation, ou null tant que le défi n'est pas accepté.
 *                  C'est par ce champ que le défiant, qui attend, apprend qu'il doit entrer en combat.
 */
public record StsPvpChallengeDto(
        UUID challengeId,
        String direction,
        UUID challengerId,
        String challengerName,
        UUID challengedId,
        String challengedName,
        boolean friendly,
        String status,
        UUID battleId,
        Instant expiresAt,
        Instant createdAt
) {
}

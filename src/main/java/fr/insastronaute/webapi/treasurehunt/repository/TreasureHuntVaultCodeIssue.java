package fr.insastronaute.webapi.treasurehunt.repository;

import java.time.Instant;
import java.util.UUID;

/** Un code du coffre remis a un joueur, sans le code : les statistiques n'ont a savoir que quand. */
public record TreasureHuntVaultCodeIssue(UUID userId, UUID pathId, Instant issuedAt) {
}

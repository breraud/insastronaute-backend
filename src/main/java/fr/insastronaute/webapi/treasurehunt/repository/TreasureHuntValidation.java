package fr.insastronaute.webapi.treasurehunt.repository;

import java.time.Instant;
import java.util.UUID;

/** Une etape validee par un joueur, reduite a ce que les statistiques en lisent. */
public record TreasureHuntValidation(UUID userId, UUID pathId, UUID checkpointId, Integer stepNumber, Instant validatedAt) {
}

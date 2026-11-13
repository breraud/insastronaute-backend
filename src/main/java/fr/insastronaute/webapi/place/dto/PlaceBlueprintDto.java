package fr.insastronaute.webapi.place.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un plan tel que l'application le lit.
 *
 * <p>{@code mine} et {@code sharedBy} tiennent ensemble : la page range les
 * plans en deux listes, et « partagé par Chloé » est ce qui distingue un plan
 * reçu d'un plan qu'on a fait soi-même.</p>
 */
public record PlaceBlueprintDto(
        UUID id,
        String name,
        int width,
        int height,
        List<String> palette,
        String cells,
        /** Le nombre de cases dessinées : c'est ce qu'il reste à poser. */
        int filledCells,
        boolean mine,
        String ownerName,
        /** Où l'auteur a posé le plan. Null tant qu'il ne l'a pas posé. */
        Integer anchorX,
        Integer anchorY,
        Instant updatedAt
) {}

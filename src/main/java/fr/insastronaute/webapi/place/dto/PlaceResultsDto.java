package fr.insastronaute.webapi.place.dto;

import java.time.Instant;
import java.util.List;

/**
 * La fresque une fois finie : ce qu'il en reste, et qui l'a faite.
 *
 * @param players les dix premiers, pas davantage : au-dela, personne ne lit.
 * @param me      la ligne du joueur courant, meme hors des dix. Nulle s'il n'a rien pose,
 *                ou si personne n'est connecte.
 */
public record PlaceResultsDto(
        Instant endedAt,
        long totalPixels,
        List<PlaceDepartmentResultDto> departments,
        List<PlacePlayerResultDto> players,
        PlacePlayerResultDto me
) {
}

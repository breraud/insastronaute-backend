package fr.insastronaute.webapi.place.dto;

import java.util.List;

/**
 * Ce que la censure a effacé, et qui l'avait dessiné.
 *
 * <p>La liste des auteurs sert la suite du geste : recouvrir sans savoir qui
 * sanctionner laisse la personne recommencer ailleurs.</p>
 */
public record PlaceCensorResultDto(
        int pixelsCleared,
        List<PlacePixelAuthorDto> authors
) {
}

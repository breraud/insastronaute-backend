package fr.insastronaute.webapi.place.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * L'auteur d'un pixel.
 *
 * <p>{@code userId} n'est rempli que pour la modération : le survol côté joueur
 * n'a besoin que d'un nom, et un identifiant de compte n'a rien à faire dans une
 * réponse publique.</p>
 *
 * @param placedAt   quand le pixel a été posé : chaque pose est horodatée au registre
 * @param department le département de l'auteur, celui dont la fresque des départements
 *                   peint la case ; {@code null} s'il n'en a pas
 */
public record PlacePixelAuthorDto(
        int x,
        int y,
        String color,
        String displayName,
        Instant placedAt,
        UUID userId,
        String department
) {
    public PlacePixelAuthorDto(int x, int y, String color, String displayName, Instant placedAt, UUID userId) {
        this(x, y, color, displayName, placedAt, userId, null);
    }

    public static PlacePixelAuthorDto forPlayers(int x, int y, String color, String displayName, Instant placedAt,
                                                 String department) {
        return new PlacePixelAuthorDto(x, y, color, displayName, placedAt, null, department);
    }
}

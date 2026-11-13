package fr.insastronaute.webapi.place.repository;

import java.util.UUID;

/** Ce qu'un joueur a pose, en un chiffre. */
public record PlacePixelCount(UUID userId, long pixels) {
}

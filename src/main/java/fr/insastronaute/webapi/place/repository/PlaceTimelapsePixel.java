package fr.insastronaute.webapi.place.repository;

/**
 * Une pose, reduite a ce qu'une image en montre.
 *
 * <p>Ni auteur ni horodatage : le rejeu suit l'ordre de la requete, et la duree
 * de la video est celle qu'on lui demande, pas celle de l'evenement. Les porter
 * quand meme couterait huit octets de plus par pose, pour rien — et exposerait
 * qui a pose quoi dans un fichier fait pour etre partage.</p>
 */
public record PlaceTimelapsePixel(int x, int y, String color) {
}

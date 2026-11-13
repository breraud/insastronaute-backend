package fr.insastronaute.webapi.place.dto;

/**
 * Une ligne du classement des joueurs.
 *
 * @param rank nul pour qui est hors classement — le staff, masque des classements
 *             comme partout ailleurs. Son chiffre lui reste montre, son rang n'existe pas.
 * @param you  calcule ici plutot que devine au nom : deux homonymes s'y trompaient tous
 *             les deux. Meme raison que dans le classement general.
 */
public record PlacePlayerResultDto(
        Integer rank,
        String name,
        String department,
        long pixels,
        boolean you
) {
}

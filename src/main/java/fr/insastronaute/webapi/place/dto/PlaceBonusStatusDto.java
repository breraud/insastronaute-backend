package fr.insastronaute.webapi.place.dto;

/**
 * De quoi afficher — ou griser — le bouton d'achat sans deviner.
 *
 * @param price ce que coûte le prochain pixel bonus
 * @param eventPoints le solde de PE du joueur : c'est en PE que se paie le pixel bonus
 * @param affordable le solde suffit-il
 * @param blockedByPreviousPurchase le dernier pixel posé était un achat : il en
 *                                  faut un normal avant de racheter
 * @param available les deux conditions réunies
 */
public record PlaceBonusStatusDto(
        long price,
        long eventPoints,
        boolean affordable,
        boolean blockedByPreviousPurchase,
        boolean available
) {
}

package fr.insastronaute.webapi.deck.domain;

/**
 * À quel point voir ce palier sort de l'ordinaire, une ouverture donnée.
 *
 * Le pourcentage seul ne le dit pas : 10 % dans un pack qui tire dix cartes se
 * voit presque à chaque ouverture, quand les mêmes 10 % dans un pack qui n'en
 * tire qu'une se font attendre dix packs. C'est cette différence-là que
 * l'animation doit refléter.
 */
public enum DropTierRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

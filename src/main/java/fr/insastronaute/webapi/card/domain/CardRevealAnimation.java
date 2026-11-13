package fr.insastronaute.webapi.card.domain;

/**
 * La mise en scène jouée quand une carte de cette catégorie se retourne.
 *
 * Un choix par catégorie plutôt que par palier : c'est la carte qu'on retourne,
 * pas la case du tableau de probabilités, et une même catégorie doit se révéler
 * pareil quel que soit le pack d'où elle sort.
 *
 * {@link #AUTO} laisse la rareté mesurée du palier décider, ce qui reste le bon
 * réglage pour les catégories ordinaires.
 */
public enum CardRevealAnimation {
    /** Réglée sur la fréquence réelle du palier dans ce pack. */
    AUTO,
    /** Sobre : la carte se retourne, rien de plus. */
    CALM,
    /** Ça pétille. */
    SPARKLE,
    /** On soigne : étincelles, vignettage, label mis en avant. */
    RADIANT,
    /** Grand jeu : rayons, secousse, anneaux, pulsation. */
    SPECTACULAR,

    // Les suivantes ne règlent pas une intensité mais un caractère : deux
    // catégories également rares peuvent mériter deux mises en scène qui ne se
    // ressemblent pas. Chacune impose au passage son propre plancher d'intensité.

    /** Balayage prismatique et franges chromatiques. */
    HOLOGRAM,
    /** Braises qui montent, air qui tremble. */
    EMBER,
    /** Le givre gagne la carte, puis éclate. */
    FROST,
    /** Champ d'étoiles en accélération, nébuleuse qui tourne. */
    COSMIC,
    /** Or qui coule sur la carte, poussière qui retombe. */
    GOLD,
    /** Décrochage numérique : l'image se déchire avant de se caler. */
    GLITCH
}

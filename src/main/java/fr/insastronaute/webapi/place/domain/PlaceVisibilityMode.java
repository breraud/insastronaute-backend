package fr.insastronaute.webapi.place.domain;

/**
 * Ce que le front joueur montre de Place.
 *
 * <p>Distinct de l'ouverture : un evenement peut etre annonce avant sa fenetre,
 * ou retire du front sans toucher a ses dates.</p>
 */
public enum PlaceVisibilityMode {
    /** L'onglet suit la fenetre de l'evenement : le comportement d'avant ce reglage. */
    AUTO,
    /** Toujours affiche. Ferme, on voit le canevas sans pouvoir poser. */
    SHOWN,
    /** Retire du front, et ferme a la pose meme pour qui garde le lien. */
    HIDDEN
}

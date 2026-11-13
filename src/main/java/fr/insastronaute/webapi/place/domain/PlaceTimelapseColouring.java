package fr.insastronaute.webapi.place.domain;

/**
 * De quelle couleur se rejoue une pose.
 *
 * <p>Il n'y a qu'une fresque : celle des departements en est une vue, pas un second
 * canevas. Rejouer « la fresque des departements » n'a donc pas de sens — rejouer les
 * memes poses dans les couleurs de leurs departements, si.</p>
 */
public enum PlaceTimelapseColouring {
    /** La couleur posee par le joueur : la fresque telle qu'elle s'est faite. */
    PLACED,
    /**
     * La couleur du departement du poseur : qui tenait quoi, au fil des heures.
     *
     * <p>Le departement est celui que l'auteur a aujourd'hui, comme dans la vue :
     * un joueur rattache apres coup colore aussi ses pixels d'avant.</p>
     */
    DEPARTMENT
}

package fr.insastronaute.webapi.place.dto;

/**
 * Ce que le front joueur doit montrer de Place.
 *
 * @param visible l'onglet apparait. Pilote par l'admin, independamment de la fenetre.
 * @param open    on peut poser. Faux hors fenetre, ou quand Place est masque.
 * @param departmentCanvas la fresque des departements est ouverte et visible.
 * @param results l'evenement est fini et son palmares est publie : le front montre les
 *                resultats a la place de la fresque. Independant de {@code visible} :
 *                ranger l'evenement n'emporte pas ses resultats.
 *
 * <p>Les deux etaient confondus : l'onglet n'existait que pendant l'evenement.
 * Un client qui ne lit que {@code visible} garde le comportement d'avant tant
 * que le reglage reste en automatique.</p>
 */
public record PlaceVisibilityDto(boolean visible, boolean open, boolean departmentCanvas, boolean results) {
}

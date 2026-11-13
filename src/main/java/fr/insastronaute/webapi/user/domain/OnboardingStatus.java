package fr.insastronaute.webapi.user.domain;

/**
 * Ou en est un joueur dans son accueil.
 *
 * <p>L'etat ne dit pas si le genre a ete choisi : le joueur reste NOT_STARTED entre
 * son choix et sa soumission, pour pouvoir revenir sur son recapitulatif. C'est
 * {@code AppUser.onboardingGenderChosenAt} qui porte cette information.</p>
 */
public enum OnboardingStatus {
    NOT_STARTED,
    PENDING,
    VALIDATED,
    REJECTED
}

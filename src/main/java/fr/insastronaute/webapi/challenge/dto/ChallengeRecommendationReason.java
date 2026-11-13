package fr.insastronaute.webapi.challenge.dto;

public enum ChallengeRecommendationReason {
    /** Sa fenêtre se referme bientôt. */
    ENDING_SOON,
    /** Des amis l'ont déjà validé. */
    DONE_BY_FRIENDS,
    /** Il appartient à une catégorie où le joueur réussit déjà. */
    STRONG_CATEGORY,
    /**
     * Défi automatique déjà entamé, qu'il ne reste qu'à finir.
     *
     * <p>Un compteur à zéro ne dit rien — c'est le cas de tous les défis qu'on
     * n'a pas commencés. Un compteur entamé, lui, désigne un effort déjà fourni
     * que le joueur risque d'abandonner sans le savoir.</p>
     */
    ALMOST_DONE
}

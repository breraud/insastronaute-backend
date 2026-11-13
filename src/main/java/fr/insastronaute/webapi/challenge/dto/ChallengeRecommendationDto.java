package fr.insastronaute.webapi.challenge.dto;

/**
 * Un défi mis en avant, et la raison qui le met en avant.
 *
 * <p>La raison compte autant que le défi : « trois de tes amis l'ont fait » et
 * « il ferme demain » ne se cliquent pas pour les mêmes motifs, et une liste qui
 * ne dit pas pourquoi elle propose ressemble à du hasard.
 */
public record ChallengeRecommendationDto(
        PlayerChallengeDto challenge,
        ChallengeRecommendationReason reason,
        /** La raison en toutes lettres, prête à afficher. */
        String detail,
        /**
         * L'avancement, de 0 à 100, quand la raison en est un — {@code null} sinon.
         *
         * <p>« Déjà 3 / 5 » se lit, mais une barre se voit sans lire : la carte
         * mise en avant est petite, et c'est l'avancement qui la justifie.</p>
         */
        Integer progressPercentage
) {

    /** Une mise en avant sans compteur : la raison suffit. */
    public static ChallengeRecommendationDto of(
            PlayerChallengeDto challenge, ChallengeRecommendationReason reason, String detail) {
        return new ChallengeRecommendationDto(challenge, reason, detail, null);
    }
}

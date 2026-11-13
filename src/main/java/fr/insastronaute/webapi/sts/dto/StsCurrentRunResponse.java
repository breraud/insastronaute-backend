package fr.insastronaute.webapi.sts.dto;

public record StsCurrentRunResponse(
        boolean hasRun,
        StsRunStateDto run,
        /**
         * Si le compte a ouvert le pool de cartes secrètes.
         *
         * <p>Porté ici plutôt que sur la seule run : le menu principal le lit alors qu'aucune
         * run n'est en cours, et c'est la réponse qu'il demande déjà.
         */
        boolean secretCardsUnlocked
) {
    /** La forme d'avant le drapeau secret, pour les appelants qui n'ont pas de compte sous la main. */
    public StsCurrentRunResponse(boolean hasRun, StsRunStateDto run) {
        this(hasRun, run, false);
    }
}

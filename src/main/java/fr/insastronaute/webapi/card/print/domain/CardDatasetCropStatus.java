package fr.insastronaute.webapi.card.print.domain;

public enum CardDatasetCropStatus {
    /** L'etiquette est connue : elle vient du dictionnaire de page ou d'un arbitrage humain. */
    LABELLED,
    /** Aucun code de la planche n'explique la lecture. Garde tel quel, jamais jete. */
    PENDING_ARBITRATION,
    /**
     * L'operateur a juge la capture inutilisable : floue, mal cadree, la mauvaise bande.
     *
     * <p>Ce n'est pas une suppression. La ligne reste, l'image reste dans le stockage, et le
     * geste se defait : l'operateur travaille au pouce sur des dizaines de decoupes a la suite,
     * et a ce rythme un effacement definitif finit par emporter ce qu'il voulait garder. Le
     * compte des ecartees est en plus une mesure du jeu de donnees -- combien de captures ont
     * ete jetees dit quelque chose de la collecte -- et une ligne effacee ne dit plus rien.
     *
     * <p>Une decoupe ecartee sort de l'export : ni etiquetee, ni tenue a l'ecart, ni en
     * arbitrage. Ce que l'operateur a juge inutilisable ne doit atteindre aucun entrainement.
     */
    DISCARDED
}

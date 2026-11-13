package fr.insastronaute.webapi.card.print.domain;

/**
 * Comment la decoupe a ete cadree dans la photo.
 *
 * <p>Une decoupe cadree a la main est, par definition, une decoupe que la segmentation
 * automatique n'a pas su traiter. C'est donc l'echantillon le plus precieux du lot -- un cas
 * difficile, designe gratuitement par le geste de l'operateur, sans qu'aucun modele soit
 * necessaire pour le trouver. Cette information n'est pas de la comptabilite : c'est un signal
 * d'entrainement, et il doit atteindre le jeu de donnees exporte.
 */
public enum CardDatasetCropFraming {
    /** La segmentation a trouve la bande seule. Le cas ordinaire, et la valeur par defaut. */
    AUTOMATIC,
    /** L'operateur a cadre la bande a la main : la segmentation avait echoue. */
    MANUAL,
    /**
     * L'operateur a aligne la bande dans un gabarit fixe.
     *
     * <p>Ni automatique -- aucune image n'est analysee -- ni manuel -- aucun geste de
     * recadrage n'est fait. La distinction n'est pas de la comptabilite : mesuree sur les
     * 176 decoupes annotees du 31 aout, la segmentation trouvait une boite utilisable dans
     * 20 a 26 % des cas quand un rectangle fixe en trouvait 35 %. Confondre les trois modes
     * rendrait impossible de savoir si le gabarit a tenu cette promesse.</p>
     */
    GUIDE
}

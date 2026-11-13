package fr.insastronaute.webapi.leaderboard.dto;

/**
 * Un departement au classement, et le detail de son score.
 *
 * <p>Le detail est celui fige a la publication : il dit exactement d'ou vient le
 * score affiche. Nul sur un classement publie avant qu'on le garde.</p>
 */
public record DepartmentLeaderboardEntryDto(
        Integer rank,
        String name,
        Long score,
        /** La somme des points des inscrits, avant formule. */
        Long rawTotal,
        Integer memberCount,
        Integer theoreticalHeadcount,
        /** Le multiplicateur applique a la somme. */
        Double coefficient,
        /** Les defis de promo, ajoutes apres la formule. */
        Long promoPoints,
        /**
         * Combien d'apprentis ont donne leurs points sans compter dans l'effectif.
         *
         * <p>Ces points sont dans {@code rawTotal} alors que leurs auteurs ne sont pas
         * dans {@code memberCount} : la formule divise donc par un effectif plus petit
         * que la population qui a marque. Le choix est assume et l'ecart est petit --
         * les apprentis sont peu nombreux -- mais un biais qu'on ne voit plus devient un
         * bug, alors il s'affiche.</p>
         *
         * <p>Nul sur un classement publie : le detail fige ne le retient pas.</p>
         */
        Integer perfCount
) {
    public DepartmentLeaderboardEntryDto(Integer rank, String name, Long score) {
        this(rank, name, score, null, null, null, null, null, null);
    }
}

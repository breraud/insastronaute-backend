package fr.insastronaute.webapi.sts.multiplayer.combat;

/**
 * Ce qu'un duel gagné ou perdu vaut, en classement et en jetons.
 *
 * <p>Le classement suit la formule d'Elo, ce qui veut dire une seule chose et c'est la seule qui
 * compte ici : <b>ce qu'un résultat rapporte dépend de qui était en face</b>. Battre plus fort que
 * soi rapporte beaucoup et coûte peu à l'adversaire ; battre plus faible ne rapporte presque rien
 * et coûte cher à celui qui perd. Le classement précédent, plat à ±25 quel que soit l'adversaire,
 * récompensait donc autant l'exploit que la victoire acquise d'avance, et pouvait se cultiver en
 * ne défiant que des débutants.
 *
 * <p>Les jetons suivent la même mesure plutôt qu'une seconde inventée à côté : ils partent d'un
 * fixe, montent avec l'exploit en se servant de la probabilité de victoire qu'Elo calcule déjà, et
 * montent aussi avec le classement du gagnant. Un barème séparé aurait fini par désigner comme
 * méritoire une victoire que le classement, lui, jugeait banale.
 *
 * <p>Les chiffres eux-mêmes vivent dans {@link StsPvpRatingRules}, donc dans
 * {@code gameplay/pvp_rating.json} : il ne reste ici que les formules.
 *
 * <p>Classe pure et sans dépendance : « combien vaut cette victoire ? » se teste sans base de
 * données, sans profil et sans combat.
 */
public final class StsPvpRating {

    /** Le classement d'un joueur qui n'en a pas encore, barème par défaut. */
    public static final int DEFAULT_ELO = StsPvpRatingRules.DEFAULT.defaultElo();

    /** L'amplitude maximale d'un résultat sur le classement, barème par défaut. */
    public static final int K_FACTOR = StsPvpRatingRules.DEFAULT.kFactor();

    /** Ce qu'une victoire rapporte quoi qu'il arrive, barème par défaut. */
    public static final long WIN_TOKENS_BASE = StsPvpRatingRules.DEFAULT.winTokensBase();

    /** Ce qu'une victoire rapporte en plus au prorata de l'exploit, barème par défaut. */
    public static final long WIN_TOKENS_UPSET_BONUS = StsPvpRatingRules.DEFAULT.winTokensUpsetBonus();

    /** Ce qu'une défaite coûte, barème par défaut. */
    public static final long LOSS_TOKENS = StsPvpRatingRules.DEFAULT.lossTokens();

    private StsPvpRating() {
    }

    /**
     * La probabilité qu'a {@code rating} de battre {@code opponentRating}, entre 0 et 1.
     *
     * <p>C'est la mesure dont tout le reste découle : le classement s'en sert pour savoir de
     * combien corriger, et les jetons pour savoir si la victoire relevait de l'exploit.
     */
    public static double expectedScore(StsPvpRatingRules rules, int rating, int opponentRating) {
        return 1d / (1d + Math.pow(10d, (opponentRating - rating) / rules.eloScale()));
    }

    public static double expectedScore(int rating, int opponentRating) {
        return expectedScore(StsPvpRatingRules.DEFAULT, rating, opponentRating);
    }

    /**
     * Ce que ce résultat ajoute au classement de {@code rating} — négatif sur une défaite.
     *
     * <p>Jamais nul tant que le barème pose un plancher : sans lui, un écart énorme fait tomber le
     * calcul à zéro après arrondi, le favori qui gagne ne gagne rien et l'outsider qui perd ne
     * perd rien — ce qui rend la défaite gratuite et invite à ne jouer que hors de portée.
     */
    public static int eloDelta(StsPvpRatingRules rules, int rating, int opponentRating, boolean won) {
        double expected = expectedScore(rules, rating, opponentRating);
        double raw = rules.kFactor() * ((won ? 1d : 0d) - expected);
        int rounded = (int) Math.round(raw);

        if (won) {
            return Math.max(rules.minimumEloMove(), rounded);
        }
        return Math.min(-rules.minimumEloMove(), rounded);
    }

    public static int eloDelta(int rating, int opponentRating, boolean won) {
        return eloDelta(StsPvpRatingRules.DEFAULT, rating, opponentRating, won);
    }

    /**
     * Le classement de {@code rating} après ce résultat, jamais négatif.
     *
     * <p>Le plancher est à zéro plutôt que de laisser filer : un classement négatif ne veut rien
     * dire et fausserait les appariements, qui raisonnent sur des écarts.
     */
    public static int ratingAfter(
            StsPvpRatingRules rules, int rating, int opponentRating, boolean won) {
        return Math.max(0, rating + eloDelta(rules, rating, opponentRating, won));
    }

    public static int ratingAfter(int rating, int opponentRating, boolean won) {
        return ratingAfter(StsPvpRatingRules.DEFAULT, rating, opponentRating, won);
    }

    /**
     * Ce que ce résultat ajoute au portefeuille — négatif sur une défaite.
     *
     * <p>Une victoire vaut trois choses additionnées : un fixe, une prime d'exploit, et une prime
     * de classement. La dernière est la relation classement-jetons du barème : mieux on est classé,
     * plus une victoire paie, y compris entre égaux — sans quoi deux joueurs à 1800 gagneraient
     * exactement ce que gagnent deux débutants qui s'affrontent.
     *
     * <p>Le montant rendu ici peut dépasser ce que le joueur possède : c'est à l'appelant de ne
     * pas descendre le portefeuille sous zéro, parce que lui seul connaît le solde. Rendre un
     * montant déjà rogné cacherait la règle dans deux endroits au lieu d'un.
     */
    public static long tokenDelta(
            StsPvpRatingRules rules, int rating, int opponentRating, boolean won) {
        if (!won) {
            return -rules.lossTokens();
        }

        double upset = 1d - expectedScore(rules, rating, opponentRating);
        long earned = rules.winTokensBase()
                + Math.round(rules.winTokensUpsetBonus() * upset)
                + eloBonus(rules, rating);

        return rules.maxWinTokens() > 0L ? Math.min(rules.maxWinTokens(), earned) : earned;
    }

    public static long tokenDelta(int rating, int opponentRating, boolean won) {
        return tokenDelta(StsPvpRatingRules.DEFAULT, rating, opponentRating, won);
    }

    /** La prime de classement, nulle sous le seuil pour ne pas taxer les débutants. */
    private static long eloBonus(StsPvpRatingRules rules, int rating) {
        int above = rating - rules.eloTokenBaseline();
        return above <= 0 ? 0L : Math.round(rules.tokensPerEloPoint() * above);
    }

    /**
     * La force d'une équipe, telle que l'autre équipe la voit : la moyenne de ses classements.
     *
     * <p>La moyenne et non le meilleur ni le pire : une équipe vaut ce que valent ses joueurs
     * ensemble, et prendre le meilleur ferait qu'ajouter un débutant à côté d'un expert rendrait
     * l'équipe plus redoutable sur le papier.
     *
     * @param ratings les classements de l'équipe ; vide vaut le classement par défaut du barème
     */
    public static int teamRating(StsPvpRatingRules rules, java.util.Collection<Integer> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return rules.defaultElo();
        }
        long total = 0L;
        for (Integer rating : ratings) {
            total += rating == null ? rules.defaultElo() : rating;
        }
        return (int) Math.round((double) total / ratings.size());
    }

    public static int teamRating(java.util.Collection<Integer> ratings) {
        return teamRating(StsPvpRatingRules.DEFAULT, ratings);
    }
}

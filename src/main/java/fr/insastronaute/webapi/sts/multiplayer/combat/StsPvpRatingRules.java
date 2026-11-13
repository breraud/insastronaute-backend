package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Le barème d'un duel : ce qu'un résultat vaut en classement, et ce que ce classement vaut en
 * jetons.
 *
 * <p>Autrefois écrit en constantes dans {@link StsPvpRating}, donc modifiable seulement par un
 * redéploiement. C'est pourtant le genre de chiffre qu'on ajuste en regardant jouer, exactement
 * comme les poids de {@code gameplay/run_score_rewards.json} : il vit maintenant dans
 * {@code gameplay/pvp_rating.json}, aux côtés des autres réglages d'équilibrage.
 *
 * <p>Chaque champ retombe sur sa valeur d'avant quand le fichier ne le nomme pas, et le fichier
 * entier est facultatif : une version des données qui ne le porte pas se joue comme avant plutôt
 * que de refuser de régler ses duels.
 *
 * @param defaultElo       le classement d'un joueur qui n'en a pas encore
 * @param kFactor          l'amplitude maximale d'un résultat sur le classement
 * @param eloScale         l'écart de classement qui vaut dix fois plus de chances de gagner
 * @param minimumEloMove   le plancher en valeur absolue d'un résultat, pour qu'aucun ne soit nul
 * @param winTokensBase    ce qu'une victoire rapporte quoi qu'il arrive
 * @param winTokensUpsetBonus ce qu'elle rapporte en plus, au prorata de l'exploit
 * @param lossTokens       ce qu'une défaite coûte, avant que le portefeuille ne borne le retrait
 * @param tokensPerEloPoint la relation classement-jetons : ce qu'un point de classement au-dessus
 *                          de {@code eloTokenBaseline} ajoute à une victoire. C'est ce qui fait
 *                          qu'un joueur mieux classé gagne davantage, indépendamment de l'exploit
 * @param eloTokenBaseline  le classement à partir duquel cette prime commence à courir
 * @param maxWinTokens      le plafond d'une victoire, ou {@code 0} pour ne pas plafonner
 * @param playerHpMultiplier multiplicateur applique aux PV de base du personnage en PVP
 */
public record StsPvpRatingRules(
        int defaultElo,
        int kFactor,
        double eloScale,
        int minimumEloMove,
        long winTokensBase,
        long winTokensUpsetBonus,
        long lossTokens,
        double tokensPerEloPoint,
        int eloTokenBaseline,
        long maxWinTokens,
        double playerHpMultiplier
) {

    /** Le barème d'avant la configuration, mot pour mot. */
    public static final StsPvpRatingRules DEFAULT =
            new StsPvpRatingRules(1000, 32, 400d, 1, 10L, 20L, 5L, 0.02d, 1000, 100L, 1d);

    public StsPvpRatingRules {
        if (kFactor <= 0 || eloScale <= 0d || playerHpMultiplier <= 0d) {
            throw new IllegalArgumentException("Le barème PVP demande des coefficients positifs.");
        }
    }

    /**
     * Le barème que porte {@code gameplay/pvp_rating.json}, complété par {@link #DEFAULT}.
     *
     * <p>Un nœud absent, nul ou d'une autre forme rend le barème par défaut : ce fichier est un
     * réglage, et un réglage illisible doit laisser le jeu tourner tel qu'il tournait.
     */
    public static StsPvpRatingRules from(JsonNode node) {
        if (node == null || !node.isObject()) {
            return DEFAULT;
        }

        JsonNode elo = node.path("elo");
        JsonNode tokens = node.path("tokens");
        JsonNode combat = node.path("combat");

        int kFactor = positiveOr(elo.path("kFactor").asInt(DEFAULT.kFactor()), DEFAULT.kFactor());
        double eloScale = elo.path("scale").asDouble(DEFAULT.eloScale());
        if (eloScale <= 0d) {
            eloScale = DEFAULT.eloScale();
        }
        double playerHpMultiplier = combat.path("playerHpMultiplier").asDouble(DEFAULT.playerHpMultiplier());
        if (playerHpMultiplier <= 0d) {
            playerHpMultiplier = DEFAULT.playerHpMultiplier();
        }

        return new StsPvpRatingRules(
                Math.max(0, elo.path("default").asInt(DEFAULT.defaultElo())),
                kFactor,
                eloScale,
                Math.max(0, elo.path("minimumMove").asInt(DEFAULT.minimumEloMove())),
                Math.max(0L, tokens.path("winBase").asLong(DEFAULT.winTokensBase())),
                Math.max(0L, tokens.path("winUpsetBonus").asLong(DEFAULT.winTokensUpsetBonus())),
                Math.max(0L, tokens.path("loss").asLong(DEFAULT.lossTokens())),
                Math.max(0d, tokens.path("perEloPoint").asDouble(DEFAULT.tokensPerEloPoint())),
                Math.max(0, tokens.path("eloBaseline").asInt(DEFAULT.eloTokenBaseline())),
                Math.max(0L, tokens.path("maxWin").asLong(DEFAULT.maxWinTokens())),
                playerHpMultiplier);
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}

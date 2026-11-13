package fr.insastronaute.webapi.sts.multiplayer.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Ce qu'une victoire vaut selon qui était en face.
 *
 * <p>C'est la règle que le classement plat de ±25 n'exprimait pas : il payait autant l'exploit que
 * la victoire acquise d'avance. Ce qui est vérifié ici est donc surtout la <em>direction</em> des
 * choses — plus fort rapporte plus, plus faible rapporte moins — plus les bornes qui empêchent le
 * barème de dégénérer aux extrêmes.
 */
class StsPvpRatingTest {

    @Test
    void equalOpponentsAreEvenMoney() {
        assertThat(StsPvpRating.expectedScore(1000, 1000)).isCloseTo(0.5d, within(1e-9));
    }

    @Test
    void fourHundredPointsOfGapIsTenToOne() {
        // La constante d'Elo : 400 points valent dix fois plus de chances de gagner.
        assertThat(StsPvpRating.expectedScore(1400, 1000)).isCloseTo(10d / 11d, within(1e-9));
        assertThat(StsPvpRating.expectedScore(1000, 1400)).isCloseTo(1d / 11d, within(1e-9));
    }

    @Test
    void aWinBetweenEqualsMovesHalfOfK() {
        assertThat(StsPvpRating.eloDelta(1000, 1000, true)).isEqualTo(StsPvpRating.K_FACTOR / 2);
        assertThat(StsPvpRating.eloDelta(1000, 1000, false)).isEqualTo(-StsPvpRating.K_FACTOR / 2);
    }

    /** Le cœur du barème : l'exploit rapporte plus que la victoire attendue. */
    @Test
    void beatingAStrongerOpponentGainsMore() {
        int upset = StsPvpRating.eloDelta(1000, 1600, true);
        int even = StsPvpRating.eloDelta(1000, 1000, true);
        int expected = StsPvpRating.eloDelta(1600, 1000, true);

        assertThat(upset).isGreaterThan(even);
        assertThat(even).isGreaterThan(expected);
    }

    /** Et sa contrepartie : perdre contre plus fort coûte moins que perdre contre plus faible. */
    @Test
    void losingToAStrongerOpponentCostsLess() {
        int toStronger = StsPvpRating.eloDelta(1000, 1600, false);
        int toWeaker = StsPvpRating.eloDelta(1600, 1000, false);

        assertThat(toStronger).isGreaterThan(toWeaker);
        assertThat(toStronger).isNegative();
    }

    /** Un duel ne crée ni ne détruit de classement, aux arrondis près. */
    @Test
    void whatOneSideGainsTheOtherRoughlyLoses() {
        int gained = StsPvpRating.eloDelta(1200, 1000, true);
        int lost = StsPvpRating.eloDelta(1000, 1200, false);

        assertThat(gained + lost).isBetween(-1, 1);
    }

    /**
     * Aux extrêmes, l'arrondi ramènerait la correction à zéro : le favori ne gagnerait rien et,
     * bien pire, l'outsider ne perdrait rien. Défier hors de portée deviendrait sans risque.
     */
    @Test
    void aResultAlwaysMovesTheRatingByAtLeastAPoint() {
        assertThat(StsPvpRating.eloDelta(3000, 100, true)).isGreaterThanOrEqualTo(1);
        assertThat(StsPvpRating.eloDelta(100, 3000, false)).isLessThanOrEqualTo(-1);
    }

    /**
     * Le plancher mord quand la correction dépasse ce qu'il reste : un joueur presque à zéro qui
     * perd contre plus faible que lui encaisse une pleine correction, et tomberait sous zéro.
     */
    @Test
    void theRatingNeverFallsBelowZero() {
        assertThat(StsPvpRating.eloDelta(5, 0, false)).isLessThan(-5);
        assertThat(StsPvpRating.ratingAfter(5, 0, false)).isZero();
        assertThat(StsPvpRating.ratingAfter(0, 3000, false)).isZero();
    }

    /**
     * Là où il ne mord pas, il ne rabote rien : perdre contre bien plus fort que soi ne coûte que
     * le minimum, même quand on n'a presque plus de classement.
     */
    @Test
    void aHopelessLossOnlyCostsTheMinimum() {
        assertThat(StsPvpRating.ratingAfter(5, 3000, false)).isEqualTo(4);
    }

    @Test
    void aWinAlwaysPaysAtLeastTheBase() {
        // Contre un adversaire qu'on ne pouvait pas perdre contre, il ne reste que le fixe et la
        // prime de classement ; barème sans prime, c'est le fixe seul qui doit rester.
        assertThat(StsPvpRating.tokenDelta(withoutEloBonus(), 3000, 100, true))
                .isBetween(StsPvpRating.WIN_TOKENS_BASE, StsPvpRating.WIN_TOKENS_BASE + 1);
    }

    /**
     * La relation classement-jetons : à exploit égal, mieux classé paie plus.
     *
     * <p>C'est ce qui distingue deux joueurs à 1800 de deux débutants : le duel est aussi serré,
     * mais il ne vaut pas la même chose.
     */
    @Test
    void aBetterRatedWinnerEarnsMoreForTheSameFeat() {
        assertThat(StsPvpRating.tokenDelta(1800, 1800, true))
                .isGreaterThan(StsPvpRating.tokenDelta(1000, 1000, true));
    }

    /** La prime de classement ne descend pas sous le seuil : un débutant n'est pas taxé. */
    @Test
    void aRatingBelowTheBaselineIsNotPenalised() {
        assertThat(StsPvpRating.tokenDelta(200, 200, true))
                .isEqualTo(StsPvpRating.tokenDelta(1000, 1000, true));
    }

    /** Le plafond du barème tient, si haut que monte le classement. */
    @Test
    void aWinNeverPaysMoreThanTheConfiguredCeiling() {
        assertThat(StsPvpRating.tokenDelta(100_000, 100_000, true))
                .isEqualTo(StsPvpRatingRules.DEFAULT.maxWinTokens());
    }

    @Test
    void anUpsetPaysTheFullBonus() {
        assertThat(StsPvpRating.tokenDelta(100, 3000, true))
                .isEqualTo(StsPvpRating.WIN_TOKENS_BASE + StsPvpRating.WIN_TOKENS_UPSET_BONUS);
    }

    @Test
    void anEvenWinPaysHalfTheBonus() {
        assertThat(StsPvpRating.tokenDelta(1000, 1000, true))
                .isEqualTo(StsPvpRating.WIN_TOKENS_BASE + StsPvpRating.WIN_TOKENS_UPSET_BONUS / 2);
    }

    /** Le barème par défaut, prime de classement retirée : ce qui reste est l'exploit seul. */
    private static StsPvpRatingRules withoutEloBonus() {
        StsPvpRatingRules base = StsPvpRatingRules.DEFAULT;
        return new StsPvpRatingRules(
                base.defaultElo(), base.kFactor(), base.eloScale(), base.minimumEloMove(),
                base.winTokensBase(), base.winTokensUpsetBonus(), base.lossTokens(),
            0d, base.eloTokenBaseline(), base.maxWinTokens(), base.playerHpMultiplier());
    }

    /** La défaite coûte un forfait : « quelques jetons », quel que soit l'adversaire. */
    @Test
    void everyLossCostsTheSameFewTokens() {
        assertThat(StsPvpRating.tokenDelta(1000, 1000, false)).isEqualTo(-StsPvpRating.LOSS_TOKENS);
        assertThat(StsPvpRating.tokenDelta(100, 3000, false)).isEqualTo(-StsPvpRating.LOSS_TOKENS);
        assertThat(StsPvpRating.tokenDelta(3000, 100, false)).isEqualTo(-StsPvpRating.LOSS_TOKENS);
    }

    /** Gagner rapporte toujours plus que perdre ne coûte : jouer reste payant. */
    @Test
    void winningAlwaysPaysMoreThanLosingCosts() {
        assertThat(StsPvpRating.tokenDelta(3000, 100, true))
                .isGreaterThan(-StsPvpRating.tokenDelta(1000, 1000, false));
    }

    @Test
    void aTeamIsWorthTheAverageOfItsPlayers() {
        assertThat(StsPvpRating.teamRating(List.of(1000, 1400))).isEqualTo(1200);
        assertThat(StsPvpRating.teamRating(List.of(1000))).isEqualTo(1000);
    }

    /**
     * La moyenne, et non le meilleur : sinon ajouter un débutant à côté d'un expert rendrait
     * l'équipe plus redoutable sur le papier qu'elle ne l'est.
     */
    @Test
    void aBeginnerDragsATeamAverageDown() {
        assertThat(StsPvpRating.teamRating(List.of(2000, 200)))
                .isLessThan(2000)
                .isEqualTo(1100);
    }

    @Test
    void anUnknownTeamIsWorthTheStartingRating() {
        assertThat(StsPvpRating.teamRating(List.of())).isEqualTo(StsPvpRating.DEFAULT_ELO);
        assertThat(StsPvpRating.teamRating(null)).isEqualTo(StsPvpRating.DEFAULT_ELO);
    }
}

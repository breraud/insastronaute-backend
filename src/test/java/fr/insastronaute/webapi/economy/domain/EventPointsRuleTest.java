package fr.insastronaute.webapi.economy.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventPointsRuleTest {

    /** Un pour un sur les tokens gagnés, deux pour un sur les défis. */
    @Test
    void tokensEarnedBringTheirEventPointsAndChallengesBringTwice() {
        assertThat(CurrencySource.GAME_RUN.eventPointsFor(40)).isEqualTo(40);
        assertThat(CurrencySource.COLLECTION_REWARD.eventPointsFor(5)).isEqualTo(5);
        assertThat(CurrencySource.ADMIN_ADJUSTMENT.eventPointsFor(10)).isEqualTo(10);
        assertThat(CurrencySource.CHALLENGE.eventPointsFor(50)).isEqualTo(100);
    }

    /**
     * Un défi retiré reprend ses PE : sans cela, valider puis retirer le même défi
     * en boucle fabriquerait des PE à partir de rien.
     */
    @Test
    void aWithdrawnChallengeTakesItsEventPointsBack() {
        assertThat(CurrencySource.CHALLENGE.eventPointsFor(-50)).isEqualTo(-100);
    }

    /** Un duel perdu est une mise, pas un gain qu'on reprend : il ne coûte aucun PE. */
    @Test
    void aLostDuelCostsNoEventPoints() {
        assertThat(CurrencySource.PVP_BATTLE.eventPointsFor(25)).isEqualTo(25);
        assertThat(CurrencySource.PVP_BATTLE.eventPointsFor(-25)).isZero();
    }

    /** Dépenser des tokens ne rapporte rien. */
    @Test
    void spendingEarnsNoEventPoints() {
        assertThat(CurrencySource.PACK_PURCHASE.eventPointsFor(-120)).isZero();
        assertThat(CurrencySource.PLACE_BONUS_PIXEL.eventPointsFor(-30)).isZero();
    }

    /**
     * Un défi retiré après que ses PE ont été dépensés ne laisse pas de dette :
     * le solde revient à zéro, et c'est ce qui a réellement bougé qu'on inscrit.
     */
    @Test
    void theBalanceNeverGoesBelowZeroAndReportsWhatActuallyMoved() {
        AppUser user = new AppUser();
        user.setEventPoints(30);

        long applied = user.creditEventPointsFor(CurrencySource.CHALLENGE, -50);

        assertThat(user.getEventPoints()).isZero();
        assertThat(applied).isEqualTo(-30);
    }
}

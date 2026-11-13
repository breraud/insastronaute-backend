package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StsPvpBattleSettlementTest {

    private static final UUID COMBAT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static StsPvpProfile profile(int elo) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setElo(elo);
        profile.setWinsRanked(0);
        profile.setLossesRanked(0);
        profile.setWinsFriendly(0);
        profile.setLossesFriendly(0);
        return profile;
    }

    /** A live two-combatant PvP state where the first one is timeline-active. */
    private static CombatState twoHumans(String firstCombatantId, String secondCombatantId) {
        return new CombatState(
                COMBAT_ID,
                1L,
                "pvp-test-v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(7L),
                List.of(
                        combatant(firstCombatantId, "team-0", 80),
                        combatant(secondCombatantId, "team-1", 80)),
                List.of(
                        new TimelineEntry(firstCombatantId, 0L, 0L),
                        new TimelineEntry(secondCombatantId, 1L, 1L)),
                firstCombatantId);
    }

    /** The same battle, once the {@code team-1} combatant has been wiped out. */
    private static CombatState secondTeamDefeated(String firstCombatantId, String secondCombatantId) {
        return new CombatState(
                COMBAT_ID,
                2L,
                "pvp-test-v1",
                CombatStatus.FINISHED,
                "team-0",
                new RandomState(7L),
                List.of(
                        combatant(firstCombatantId, "team-0", 80),
                        combatant(secondCombatantId, "team-1", 0)),
                List.of(
                        new TimelineEntry(firstCombatantId, 0L, 0L),
                        new TimelineEntry(secondCombatantId, 1L, 1L)),
                null);
    }

    private static CombatantState combatant(String combatantId, String teamId, int hp) {
        return new CombatantState(
                combatantId,
                teamId,
                ControllerType.HUMAN,
                80,
                hp,
                0,
                3,
                3,
                25,
                new CardPiles(
                        List.of(new CardInstance(combatantId + "-draw-1", "card-a")),
                        List.of(new CardInstance(combatantId + "-hand-1", "card-b")),
                        List.of(),
                        List.of()));
    }

    @Test
    void aRankedWinMovesTheRatingBothWays() {
        StsPvpProfile winner = profile(1000);
        StsPvpProfile loser = profile(1000);

        applyRating(winner, loser, false);

        // Entre égaux, la correction vaut la moitié de K.
        assertThat(winner.getElo()).isEqualTo(1016);
        assertThat(loser.getElo()).isEqualTo(984);
        assertThat(winner.getWinsRanked()).isEqualTo(1);
        assertThat(loser.getLossesRanked()).isEqualTo(1);
        assertThat(winner.getWinsFriendly()).isZero();
        assertThat(loser.getLossesFriendly()).isZero();
    }

    /**
     * Le fond du classement : ce qu'un résultat vaut dépend de qui était en face. Battre bien
     * plus fort que soi rapporte beaucoup plus que battre bien plus faible.
     */
    @Test
    void beatingAStrongerOpponentIsWorthMoreThanBeatingAWeakerOne() {
        StsPvpProfile underdog = profile(1000);
        StsPvpProfile favourite = profile(1400);
        applyRating(underdog, favourite, false);
        int upsetGain = underdog.getElo() - 1000;

        StsPvpProfile strong = profile(1400);
        StsPvpProfile weak = profile(1000);
        applyRating(strong, weak, false);
        int expectedWinGain = strong.getElo() - 1400;

        assertThat(upsetGain).isGreaterThan(expectedWinGain);
        assertThat(expectedWinGain).isPositive();
    }

    @Test
    void theRatingNeverGoesNegative() {
        StsPvpProfile winner = profile(1000);
        StsPvpProfile loser = profile(0);

        applyRating(winner, loser, false);

        assertThat(loser.getElo()).isZero();
    }

    /**
     * Un écart énorme fait tomber la correction à zéro après arrondi. Sans plancher, l'outsider
     * qui perd ne perdrait rien : la défaite deviendrait gratuite, et défier hors de portée un
     * pari sans risque.
     */
    @Test
    void aResultAlwaysMovesTheRatingByAtLeastAPoint() {
        StsPvpProfile hopeless = profile(100);
        StsPvpProfile untouchable = profile(3000);

        applyRating(untouchable, hopeless, false);

        assertThat(untouchable.getElo()).isGreaterThan(3000);
        assertThat(hopeless.getElo()).isLessThan(100);
    }

    @Test
    void aFriendlyWinOnlyMovesTheFriendlyCounters() {
        StsPvpProfile winner = profile(1000);
        StsPvpProfile loser = profile(1000);

        List<StsPvpBattleRewards.Entry> rewards = applyRating(winner, loser, true);

        assertThat(winner.getElo()).isEqualTo(1000);
        assertThat(loser.getElo()).isEqualTo(1000);
        assertThat(winner.getWinsFriendly()).isEqualTo(1);
        assertThat(loser.getLossesFriendly()).isEqualTo(1);
        assertThat(winner.getWinsRanked()).isZero();
        assertThat(loser.getLossesRanked()).isZero();
        // Rien à verser : un duel amical s'arrange à volonté, et tout gain s'y récolterait de même.
        assertThat(rewards).isEmpty();
    }

    /** Le duel écrit ce qu'il a valu, des deux côtés. */
    @Test
    void reportsWhatEachSideGainedOrLost() {
        StsPvpProfile winner = profile(1000);
        StsPvpProfile loser = profile(1000);

        List<StsPvpBattleRewards.Entry> rewards = applyRating(winner, loser, false);

        assertThat(rewards).hasSize(2);
        StsPvpBattleRewards.Entry won = rewards.stream().filter(StsPvpBattleRewards.Entry::won)
                .findFirst().orElseThrow();
        StsPvpBattleRewards.Entry lost = rewards.stream().filter(entry -> !entry.won())
                .findFirst().orElseThrow();

        assertThat(won.eloBefore()).isEqualTo(1000);
        assertThat(won.eloAfter()).isEqualTo(1016);
        assertThat(won.eloDelta()).isEqualTo(16);
        assertThat(won.tokenDelta()).isPositive();

        assertThat(lost.eloDelta()).isNegative();
        assertThat(lost.tokenDelta()).isEqualTo(-StsPvpRating.LOSS_TOKENS);
    }

    private static List<StsPvpBattleRewards.Entry> applyRating(
            StsPvpProfile winner, StsPvpProfile loser, boolean friendly) {
        return StsPvpBattleSettlement.applyTeamRating(
                List.of(new StsPvpBattleSettlement.RatedPlayer(UUID.randomUUID(), winner)),
                List.of(new StsPvpBattleSettlement.RatedPlayer(UUID.randomUUID(), loser)),
                friendly);
    }

}

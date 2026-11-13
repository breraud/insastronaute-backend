package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abandonner, quand on n'est pas seul dans son equipe.
 *
 * <p>C'est le changement de comportement le plus risque de tout le multijoueur en equipe : jusqu'a
 * present, un abandon terminait le combat sur-le-champ, ce qui etait exact tant qu'une equipe
 * comptait un joueur et un seul. Applique tel quel a un 2v2, il mettait fin a la partie de
 * l'equipier — qui n'avait rien abandonne — et deplacait le classement de quatre personnes sur la
 * decision d'une seule.
 */
class StsPvpTeamConcessionTest {

    private static final UUID COMBAT_ID = UUID.fromString("0e5f9a2c-0000-4000-8000-000000000002");
    private static final String DATA_VERSION = "pvp-test-v1";

    @Test
    void aPlayerLeavingATeamOfTwoDoesNotEndTheCombat() {
        CombatState live = twoVersusTwo();

        StsPvpBattleSettlement.Conceded conceded = StsPvpBattleSettlement.conceded(live, "a1");

        assertThat(conceded.applied()).isTrue();
        assertThat(conceded.finished()).isFalse();
        assertThat(conceded.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(conceded.state().winnerTeamId()).isNull();
        // Le partant est sorti du combat, son equipier tient toujours.
        assertThat(hpOf(conceded.state(), "a1")).isZero();
        assertThat(hpOf(conceded.state(), "a2")).isPositive();
    }

    /**
     * L'etat ne doit jamais designer un mort comme actif : {@link CombatState} refuse de se
     * construire ainsi, donc rendre la main est une obligation et pas une politesse.
     */
    @Test
    void handsTheTurnOverWhenTheQuitterHeldIt() {
        CombatState live = twoVersusTwo();
        String active = live.activeCombatantId();

        CombatState after = StsPvpBattleSettlement.conceded(live, active).state();

        assertThat(after.activeCombatantId()).isNotEqualTo(active);
        assertThat(hpOf(after, after.activeCombatantId())).isPositive();
    }

    /** Le dernier d'une equipe qui s'en va, lui, termine bien le combat. */
    @Test
    void theLastPlayerOfATeamLeavingEndsIt() {
        CombatState live = twoVersusTwo();

        CombatState afterFirst = StsPvpBattleSettlement.conceded(live, "a1").state();
        StsPvpBattleSettlement.Conceded second = StsPvpBattleSettlement.conceded(afterFirst, "a2");

        assertThat(second.finished()).isTrue();
        assertThat(second.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(second.state().winnerTeamId()).isEqualTo("team-1");
        assertThat(second.events()).hasSize(1).first().isInstanceOf(CombatEnded.class);
        assertThat(StsPvpBattleSettlement.winningTeamIndex(second.state())).hasValue(1);
    }

    /**
     * Une concession sur un combat deja fini ne doit rien faire — ni etat, ni annonce, ni
     * classement. {@code applied} le dit, la ou une liste d'evenements vide ne pouvait plus le
     * dire depuis qu'une concession peut legitimement n'avoir rien a annoncer.
     */
    @Test
    void concedingAFinishedCombatChangesNothing() {
        CombatState live = twoVersusTwo();
        CombatState finished = StsPvpBattleSettlement.conceded(
                StsPvpBattleSettlement.conceded(live, "a1").state(), "a2").state();

        StsPvpBattleSettlement.Conceded again = StsPvpBattleSettlement.conceded(finished, "b1");

        assertThat(again.applied()).isFalse();
        assertThat(again.state()).isSameAs(finished);
        assertThat(again.events()).isEmpty();
    }

    /**
     * Une équipe vaut la moyenne de ses classements, et chacun de ses joueurs est réglé contre
     * cette force-là. Un 2v2 entre quatre joueurs de même niveau se règle donc exactement comme
     * le duel correspondant.
     */
    @Test
    void ratesEveryPlayerAgainstTheOpposingTeamAverage() {
        StsPvpProfile winnerOne = profile(1000);
        StsPvpProfile winnerTwo = profile(1000);
        StsPvpProfile loserOne = profile(1000);
        StsPvpProfile loserTwo = profile(1000);

        StsPvpBattleSettlement.applyTeamRating(
                rated(winnerOne, winnerTwo), rated(loserOne, loserTwo), false);

        assertThat(winnerOne.getElo()).isEqualTo(1016);
        assertThat(winnerTwo.getElo()).isEqualTo(1016);
        assertThat(loserOne.getElo()).isEqualTo(984);
        assertThat(loserTwo.getElo()).isEqualTo(984);
        assertThat(winnerOne.getWinsRanked()).isEqualTo(1);
        assertThat(loserTwo.getLossesRanked()).isEqualTo(1);
    }

    /**
     * La force des deux camps est lue avant que quoi que ce soit ne bouge. Sinon le premier
     * gagnant réglé changerait la moyenne servant à évaluer le second, et l'ordre de la liste
     * déciderait des gains.
     */
    @Test
    void ratesTeammatesOfEqualStandingIdentically() {
        StsPvpProfile winnerOne = profile(1200);
        StsPvpProfile winnerTwo = profile(1200);

        StsPvpBattleSettlement.applyTeamRating(
                rated(winnerOne, winnerTwo), rated(profile(900), profile(900)), false);

        assertThat(winnerOne.getElo()).isEqualTo(winnerTwo.getElo());
    }

    /**
     * Un raid n'a personne du cote du boss : sans cette garde, le camp gagnant encaisserait des
     * points pris a une equipe vide.
     */
    @Test
    void movesNothingWhenOneSideHasNoPlayerAtAll() {
        StsPvpProfile winner = profile(1000);

        assertThat(StsPvpBattleSettlement.applyTeamRating(rated(winner), List.of(), false))
                .isEmpty();

        assertThat(winner.getElo()).isEqualTo(1000);
        assertThat(winner.getWinsRanked()).isZero();
    }

    private static List<StsPvpBattleSettlement.RatedPlayer> rated(StsPvpProfile... profiles) {
        List<StsPvpBattleSettlement.RatedPlayer> players = new ArrayList<>();
        for (StsPvpProfile profile : profiles) {
            players.add(new StsPvpBattleSettlement.RatedPlayer(UUID.randomUUID(), profile));
        }
        return players;
    }

    private static int hpOf(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .map(CombatantState::hp)
                .findFirst()
                .orElseThrow();
    }

    private static StsPvpProfile profile(int elo) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setElo(elo);
        profile.setWinsRanked(0);
        profile.setLossesRanked(0);
        profile.setWinsFriendly(0);
        profile.setLossesFriendly(0);
        return profile;
    }

    /** Un combat a quatre, deux par equipe, monte par le vrai moteur. */
    private static CombatState twoVersusTwo() {
        CombatSetup setup = new CombatSetup(COMBAT_ID, DATA_VERSION, 20260901L, List.of(
                combatant("a1", 0), combatant("a2", 0),
                combatant("b1", 1), combatant("b2", 1)));
        return new CombatFactory(StsPvpTestStates.catalog(), new RelicEngine(List.of()))
                .create(setup)
                .state();
    }

    private static CombatantSetup combatant(String combatantId, int teamIndex) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            deck.add(new CardInstance(combatantId + "-card-" + index, "strike"));
        }
        return new CombatantSetup(
                combatantId,
                StsPvpCombatSetupFactory.teamId(teamIndex),
                ControllerType.HUMAN,
                StsPvpCombatSetupFactory.PVP_MAX_HP,
                StsPvpCombatSetupFactory.PVP_BASE_ENERGY,
                StsPvpCombatSetupFactory.PVP_TURN_DELAY_TICKS,
                0L,
                deck,
                List.of());
    }
}

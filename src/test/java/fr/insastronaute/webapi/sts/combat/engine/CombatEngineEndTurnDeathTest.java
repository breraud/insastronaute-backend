package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A status that kills as a turn changes hands ends the combat.
 *
 * <p>Ending a turn was the one handler that never asked whether the combat was still on. A Burn
 * that finished the last enemy off, or the last player, left the handler building an active state
 * with a single living team — which the state refuses, out of the message handler, leaving the
 * command unanswered and the combat stuck on that revision for good. Reported from production
 * against a lone enemy on two health carrying a Burn of five.
 */
class CombatEngineEndTurnDeathTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";

    @Test
    void endsTheCombatWhenTheLastEnemyBurnsToDeathClosingItsOwnTurn() {
        CombatResult result = endTurn(
                state(ENEMY, 30, List.of(), 2, List.of(burn(5))), ENEMY);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), PLAYERS));
    }

    @Test
    void keepsOnlyTheLivingOnTheTimelineOfACombatEndedByAnEndOfTurnDeath() {
        CombatResult result = endTurn(
                state(ENEMY, 30, List.of(), 2, List.of(burn(5))), ENEMY);

        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO);
    }

    @Test
    void endsTheCombatWhenTheLastEnemyIsPoisonedToDeathOpeningItsTurn() {
        CombatResult result = endTurn(
                state(HERO, 30, List.of(), 2, List.of(poison(5))), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), PLAYERS));
    }

    @Test
    void handsTheCombatToTheEnemiesWhenTheEndOfTurnKillsThePlayer() {
        CombatResult result = endTurn(
                state(HERO, 2, List.of(burn(5)), 30, List.of()), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(ENEMIES);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), ENEMIES));
    }

    /**
     * A combat is over the moment a side falls, so the turn it would have handed on never opens
     * and the statuses waiting there never run. Both sides are one tick from death here, and the
     * poisoned enemy outlives the burning player it was about to follow rather than joining it in
     * a draw.
     */
    @Test
    void stopsOnTheFirstDeathInsteadOfOpeningATurnThatWouldKillAgain() {
        CombatResult result = endTurn(
                state(HERO, 2, List.of(burn(5)), 2, List.of(poison(5))), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(ENEMIES);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(ENEMY);
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(2);
        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), ENEMIES));
    }

    /**
     * A stunned combatant dies of its own statuses inside the same revision that skips it, and the
     * combat is over there too. The rule that stops at the first death was written for the turn
     * the ending actor hands on; the turns lost to a stun in between were left out of it, so the
     * fight ran on past its own end and opened the turn behind the corpse. Here the last enemy is
     * poisoned to death on the turn it was too stunned to take, and the player it had already lost
     * to is poisoned in turn on a turn that should never have opened — a won fight recorded as a
     * draw, and a player killed by an enemy that was already dead.
     */
    @Test
    void stopsOnAStunnedCombatantDyingInsteadOfOpeningTheTurnBehindIt() {
        CombatResult result = endTurn(
                state(HERO, 2, List.of(poison(5)), 2, List.of(stun(), poison(5))), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(combatant(result.state(), HERO).hp()).isEqualTo(2);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO);
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), PLAYERS));
    }

    /** The turn the stun cost is still reported; only the one behind it is not. */
    @Test
    void stillReportsTheTurnTheStunCostBeforeEndingTheCombatOnIt() {
        CombatResult result = endTurn(
                state(HERO, 2, List.of(poison(5)), 2, List.of(stun(), poison(5))), HERO);

        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY);
    }

    @Test
    void leavesATurnThatKillsNobodyRunningAsBefore() {
        CombatResult result = endTurn(state(HERO, 30, List.of(), 30, List.of()), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(result.events()).noneMatch(event -> event instanceof CombatEnded);
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult endTurn(CombatState state, String actorId) {
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    /** Burn strikes for its remaining duration as the turn it is carried through closes. */
    private static StatusInstance burn(int damage) {
        return new StatusInstance("Burn", 0, damage, null, 0);
    }

    /** Poison strikes for its remaining duration as its holder's turn opens. */
    private static StatusInstance poison(int damage) {
        return new StatusInstance("Poison", 0, damage, null, 0);
    }

    /** Stun costs its holder the one turn it is carried through. */
    private static StatusInstance stun() {
        return new StatusInstance("Stun", 1, 1, null, 0);
    }

    private static CombatState state(
            String activeCombatantId,
            int heroHp,
            List<StatusInstance> heroStatuses,
            int enemyHp,
            List<StatusInstance> enemyStatuses
    ) {
        CombatantState hero = combatant(
                HERO, PLAYERS, ControllerType.HUMAN, heroHp, heroStatuses);
        CombatantState enemy = combatant(
                ENEMY, ENEMIES, ControllerType.AI, enemyHp, enemyStatuses);
        boolean heroActs = HERO.equals(activeCombatantId);
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(
                        new TimelineEntry(activeCombatantId, 0, 0),
                        new TimelineEntry(heroActs ? ENEMY : HERO, 50, 1)),
                activeCombatantId);
    }

    private static CombatantState combatant(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                combatantId,
                teamId,
                controllerType,
                30,
                hp,
                0,
                0,
                3,
                100,
                new CardPiles(
                        List.of(new CardInstance(combatantId + "-spare", "any")),
                        List.of(),
                        List.of(),
                        List.of()),
                statuses,
                List.of(),
                null,
                0);
    }
}

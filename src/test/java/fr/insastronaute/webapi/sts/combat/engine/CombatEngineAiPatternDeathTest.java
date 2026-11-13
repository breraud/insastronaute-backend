package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An enemy killing the player says so, like every other way a combat can end.
 *
 * <p>Playing an authored enemy move was the one handler that read the outcome, wrote a finished
 * state from it, and then never announced it: no {@code CombatEnded} left the engine. Every
 * combat the enemies win goes through this handler, because the AI plays its moves through it
 * and nothing else, so the client was told the fight was over only by the state it was handed
 * and never by the event the defeat screen waits for.
 */
class CombatEngineAiPatternDeathTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";
    private static final String SLAM = "slam";

    @Test
    void announcesTheCombatEndedWhenTheEnemyMoveKillsThePlayer() {
        CombatResult result = playPattern(state(1));

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(ENEMIES);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), ENEMIES));
    }

    /** The announcement comes last, once everything the killing move did has been reported. */
    @Test
    void announcesTheEndAfterTheDamageThatCausedIt() {
        CombatResult result = playPattern(state(1));

        assertThat(result.events().getLast()).isInstanceOf(CombatEnded.class);
    }

    /**
     * Ending the combat is added to what this handler reports, not substituted for it: the
     * timeline it had already cleared of the dead and the counters it had already tallied are
     * the ones the finished state carries.
     */
    @Test
    void keepsTheTimelineAndCountersTheEnemyMoveProduced() {
        CombatResult result = playPattern(state(1));

        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(ENEMY);
        assertThat(result.state().counters().hpLostSinceLastTurn()).containsEntry(HERO, 1);
        assertThat(result.state().counters().energySpentThisTurn()).isEqualTo(1);
    }

    @Test
    void saysNothingWhenTheEnemyMoveLeavesThePlayerStanding() {
        CombatResult result = playPattern(state(30));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().winnerTeamId()).isNull();
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(result.events()).noneMatch(CombatEnded.class::isInstance);
    }

    private static CombatResult playPattern(CombatState state) {
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(SLAM, new CardDefinition(
                        SLAM, 1, TargetingMode.SINGLE_ENEMY,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 5))))),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state,
                new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), ENEMY, SLAM, List.of(HERO), 1));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatState state(int heroHp) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, heroHp),
                        combatant(ENEMY, ENEMIES, ControllerType.AI, 20)),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(HERO, 50, 1)),
                ENEMY);
    }

    private static CombatantState combatant(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp
    ) {
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}

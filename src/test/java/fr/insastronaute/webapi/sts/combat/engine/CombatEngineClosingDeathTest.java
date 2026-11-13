package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
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
 * A caster that loses the hand mid-action closes its turn, and that closing can kill it. The
 * combat is over where it does.
 *
 * <p>{@code closeTheTurnOf} runs the end-of-turn statuses, Burn included, so the caster it was
 * pulled out for can die in it. {@code endTurn} asks {@link CombatStatus} the question again right
 * after its own closing ceremony, for exactly that reason; the two handlers that call the same
 * ceremony mid-card did not, and went straight to the hand-over.
 *
 * <p>What that costs, in an ordinary solo encounter: you carry a lethal Burn, you play a card that
 * pulls the enemy ahead of you on the timeline, the Burn kills you as the turn closes — and the
 * engine opens a full turn for the enemy inside a combat it has already won. Armour cleared, a
 * hand dealt, its own statuses run. If its own Poison finishes it there, the fight is recorded as a
 * draw instead of the win it was, and the enemy is killed by a player who was already dead. This is
 * the same defect {@code CombatEngineEndTurnDeathTest} pins for the end-turn path and the
 * stun-skip path; these tests pin it for the two card paths.
 *
 * <p>The trigger is {@code AdvanceTurn} aimed at the <em>other</em> combatant rather than at the
 * caster itself: a self-aimed {@code DelayTurn}/{@code AdvanceTurn} no longer moves the caster's
 * own live entry (see {@link CombatEngineDelayedCasterTest}), so it can no longer cost the caster
 * its place by itself. Pulling somebody else ahead of the caster still can, and still exercises the
 * exact closing-can-kill path this file pins.
 */
class CombatEngineClosingDeathTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e4");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";
    private static final String ADVANCE_OTHER = "advance-other";

    /// The player is alone on its team and dies of its own Burn as the turn it just lost
    /// closes. The enemy has won, and it must be recorded as its win — not as the draw the
    /// turn it should never have been given would have made of it.
    @Test
    void endsTheCombatWhenTheClosingCeremonyKillsTheLoneCaster() {
        CombatResult result = playAdvanceOther(loneHeroBurningBehindAPoisonedEnemy());

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(ENEMIES);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), ENEMIES));
    }

    /// And nothing of the enemy's turn ran: no armour reset, no hand dealt, no statuses. The
    /// proof is that its own lethal Poison never bit — a turn that had opened would have
    /// killed it and turned the win above into a draw.
    @Test
    void opensNoTurnInsideACombatDecidedByTheClosingCeremony() {
        CombatResult result = playAdvanceOther(loneHeroBurningBehindAPoisonedEnemy());

        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
        assertThat(combatant(result.state(), ENEMY).hp()).isEqualTo(2);
        assertThat(combatant(result.state(), ENEMY).piles().draw()).hasSize(5);
        assertThat(combatant(result.state(), ENEMY).piles().hand()).hasSize(1);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(ENEMY);
    }

    /// The same rule on the authored-move path, which carries its own copy of it: the enemy
    /// is alone on its side, pulls the player ahead of it on the timeline and burns to death
    /// closing the turn. The player has won.
    @Test
    void endsTheCombatWhenTheClosingCeremonyKillsTheLoneAiCaster() {
        CombatResult result = playAiAdvanceOther(loneEnemyBurningBehindAPoisonedHero());

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .contains(new CombatEnded(COMBAT_ID, result.state().revision(), PLAYERS));
    }

    @Test
    void opensNoTurnInsideACombatDecidedByAnAiCastersClosingCeremony() {
        CombatResult result = playAiAdvanceOther(loneEnemyBurningBehindAPoisonedHero());

        assertThat(result.events()).noneMatch(TurnStarted.class::isInstance);
        assertThat(combatant(result.state(), HERO).hp()).isEqualTo(2);
        assertThat(result.state().timeline())
                .extracting(TimelineEntry::combatantId)
                .containsExactly(HERO);
    }

    /// The common case, and the detector of a guard applied too widely: the same card, the
    /// same lost hand, a caster the closing ceremony does not kill. The hand-over happens as
    /// before and the combat runs on.
    @Test
    void stillHandsTheTurnOnWhenTheClosingCeremonyKillsNobody() {
        CombatResult result = playAdvanceOther(state(
                HERO, 30, List.of(), 30, List.of()));

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY);
    }

    // ------------------------------------------------------------------------ fixtures

    /** Player alone on its side, two health and a Burn of five, one enemy close behind on
     *  two health and a Poison of five. Both are one tick from death; only one of them is
     *  owed a tick. */
    private static CombatState loneHeroBurningBehindAPoisonedEnemy() {
        return state(HERO, 2, List.of(burn(5)), 2, List.of(poison(5)));
    }

    /** The mirror image, for the authored-move path. */
    private static CombatState loneEnemyBurningBehindAPoisonedHero() {
        return state(ENEMY, 2, List.of(poison(5)), 2, List.of(burn(5)));
    }

    /// Whoever acts sits at tick twenty, the other a hundred ticks behind: far enough that
    /// pulling the other one all the way up to tick zero puts it ahead of the actor.
    private static CombatState state(
            String activeCombatantId,
            int heroHp,
            List<StatusInstance> heroStatuses,
            int enemyHp,
            List<StatusInstance> enemyStatuses
    ) {
        boolean heroActs = HERO.equals(activeCombatantId);
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(
                        combatant(HERO, PLAYERS, ControllerType.HUMAN, heroHp, heroStatuses),
                        combatant(ENEMY, ENEMIES, ControllerType.AI, enemyHp, enemyStatuses)),
                List.of(
                        new TimelineEntry(activeCombatantId, 20, 0),
                        new TimelineEntry(heroActs ? ENEMY : HERO, 100, 1)),
                activeCombatantId);
    }

    private static String otherOf(String combatantId) {
        return HERO.equals(combatantId) ? ENEMY : HERO;
    }

    /** Holds the advancing card in hand and five more in the draw pile, so a turn opened for
     *  it would visibly deal a hand. */
    private static CombatantState combatant(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            List<StatusInstance> statuses
    ) {
        List<CardInstance> draw = List.of(
                new CardInstance(combatantId + "-0", "other-card"),
                new CardInstance(combatantId + "-1", "other-card"),
                new CardInstance(combatantId + "-2", "other-card"),
                new CardInstance(combatantId + "-3", "other-card"),
                new CardInstance(combatantId + "-4", "other-card"));
        return new CombatantState(
                combatantId, teamId, controllerType, 30, hp, 0, 3, 3, 100,
                new CardPiles(draw, List.of(advancingCard(combatantId)), List.of(), List.of()),
                statuses);
    }

    /** The card that pulls the opposing combatant ahead of whoever plays it. */
    private static CardInstance advancingCard(String combatantId) {
        return new CardInstance(combatantId + "-played", ADVANCE_OTHER);
    }

    private static CombatEngine engine() {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(ADVANCE_OTHER, new CardDefinition(
                        ADVANCE_OTHER, 1, TargetingMode.SINGLE_ENEMY,
                        List.of(new EffectDefinition(
                                EffectType.ADVANCE_TURN, 100, false, null, 0, null, null))))),
                new RelicEngine(List.of()));
    }

    private static CombatResult playAdvanceOther(CombatState state) {
        String actorId = state.activeCombatantId();
        CombatResult result = engine().handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID, state.revision(), actorId,
                        advancingCard(actorId).instanceId(),
                        List.of(otherOf(actorId))));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatResult playAiAdvanceOther(CombatState state) {
        String actorId = state.activeCombatantId();
        CombatResult result = engine().handle(
                state,
                new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), actorId,
                        ADVANCE_OTHER, List.of(otherOf(actorId))));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
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


    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}

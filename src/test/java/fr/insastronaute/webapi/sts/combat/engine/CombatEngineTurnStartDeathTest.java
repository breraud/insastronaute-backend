package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A turn that kills the combatant it was just opened for opens the next one in full.
 *
 * <p>Opening a turn is a ceremony: armour cleared, energy restored, start-of-turn statuses run,
 * five cards dealt, {@code TurnStarted} announced. A Poison striking as that turn opens can finish
 * off the very combatant being handed the turn, and when its team survives it, the combat carries
 * on and somebody else has to act.
 *
 * <p>Before this, the handler simply handed the state a preferred actor that was already a corpse,
 * and the state quietly fell back to the first living entry. That produced a legal state saying
 * the wrong thing: a combatant became the active one with no {@code TurnStarted} of its own, no
 * energy, no hand and its armour still standing, right after a {@code TurnStarted} had been
 * announced for the body. Nothing threw, so nothing said it, and Unity was left showing a turn
 * opened for the dead and a turn to play for somebody who had never been given one.
 *
 * <p>The turn is opened again instead, wholly, for whoever takes it.
 */
class CombatEngineTurnStartDeathTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final String HERO = "hero";
    private static final String ENEMY_A = "enemyA";
    private static final String ENEMY_B = "enemyB";
    private static final String ENEMY_C = "enemyC";
    private static final String PLAYERS = "players";
    private static final String ENEMIES = "enemies";

    @Test
    void opensTheNextTurnInFullWhenTheOneItJustOpenedKilledItsOwnHolder() {
        CombatResult result = endTurn(state(), HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(combatant(result.state(), ENEMY_A).hp()).isZero();
        assertThat(result.state().activeCombatantId()).isEqualTo(ENEMY_B);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY_A, ENEMY_B);
    }

    /** The one that ends up acting was given everything a turn gives. */
    @Test
    void givesTheCombatantThatEndsUpActingItsEnergyHandAndArmourClearing() {
        CombatantState acting = combatant(endTurn(state(), HERO).state(), ENEMY_B);

        assertThat(acting.energy()).isEqualTo(acting.baseEnergy());
        assertThat(acting.piles().hand()).hasSize(5);
        assertThat(acting.armor()).isZero();
    }

    /**
     * A combat where every opening kills its holder still ends, because the death that ends the
     * combat is asked about before the next turn opens.
     */
    @Test
    void endsTheCombatWhenTheLastOpeningKillsTheLastOfATeam() {
        CombatState state = state(
                combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 0, List.of()),
                combatant(ENEMY_A, ENEMIES, ControllerType.AI, 2, 0, List.of(poison(5))),
                combatant(ENEMY_B, ENEMIES, ControllerType.AI, 2, 0, List.of(poison(5))),
                combatant(ENEMY_C, ENEMIES, ControllerType.AI, 2, 0, List.of(poison(5))));

        CombatResult result = endTurn(state, HERO);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo(PLAYERS);
        assertThat(result.state().activeCombatantId()).isNull();
        assertThat(result.events())
                .filteredOn(CombatEnded.class::isInstance)
                .hasSize(1);
        assertThat(result.events())
                .filteredOn(TurnStarted.class::isInstance)
                .extracting(event -> ((TurnStarted) event).combatantId())
                .containsExactly(ENEMY_A, ENEMY_B, ENEMY_C);
    }

    /**
     * A turn cut short by the death of its own holder was never closed, so nothing of its end of
     * turn runs and none is reported — the same reading as a caster killed by its own card. Only
     * the hero, whose turn was ended by the command, has one.
     */
    @Test
    void reportsNoEndOfTurnForTheHolderTheOpeningKilled() {
        CombatResult result = endTurn(state(), HERO);

        assertThat(result.events())
                .filteredOn(TurnEnded.class::isInstance)
                .extracting(event -> ((TurnEnded) event).combatantId())
                .containsExactly(HERO);
    }

    /**
     * One turn was played out, so the tally advances once — not once per opening that failed.
     */
    @Test
    void countsOneTurnForTheOneTurnThatWasActuallyHandedOn() {
        CombatResult result = endTurn(state(), HERO);

        assertThat(result.state().counters().turnCount())
                .isEqualTo(state().counters().turnCount() + 1);
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

    /** Poison strikes for its remaining duration as its holder's turn opens. */
    private static StatusInstance poison(int damage) {
        return new StatusInstance("Poison", 0, damage, null, 0);
    }

    /**
     * The hero hands the turn on to an enemy that its own Poison kills as the turn opens, with a
     * living ally behind it: the combat runs on and the ally has to be given the turn in full.
     */
    private static CombatState state() {
        return state(
                combatant(HERO, PLAYERS, ControllerType.HUMAN, 30, 0, List.of()),
                combatant(ENEMY_A, ENEMIES, ControllerType.AI, 2, 0, List.of(poison(5))),
                combatant(ENEMY_B, ENEMIES, ControllerType.AI, 30, 4, List.of()));
    }

    private static CombatState state(CombatantState... combatants) {
        List<TimelineEntry> timeline = new ArrayList<>();
        for (int i = 0; i < combatants.length; i++) {
            timeline.add(new TimelineEntry(combatants[i].combatantId(), i * 10L, i));
        }
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(combatants),
                timeline,
                combatants[0].combatantId());
    }

    private static CombatantState combatant(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int hp,
            int armor,
            List<StatusInstance> statuses
    ) {
        List<CardInstance> draw = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            draw.add(new CardInstance(combatantId + "-card-" + i, "any"));
        }
        return new CombatantState(
                combatantId,
                teamId,
                controllerType,
                30,
                hp,
                armor,
                0,
                3,
                1000,
                new CardPiles(draw, List.of(), List.of(), List.of()),
                statuses,
                List.of(),
                null,
                0);
    }
}

package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
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
 * A stunned combatant loses its turn.
 *
 * <p>Unity ends the turn the moment it starts, for the player ({@code TurnSystem.cs:177-183}) and
 * for an enemy ({@code TurnSystem.cs:201-207}) alike: no cards are drawn and no move is played.
 * The status was in the catalog, applied by Delayed Stun and shown to the player, and nothing in
 * the engine read it — a stunned combatant took its turn as usual.
 */
class CombatEngineStunTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void passesOverAStunnedCombatantAndGivesTheTurnToTheNextOne() {
        CombatResult result = endHeroTurn();

        assertThat(result.state().activeCombatantId()).isEqualTo(HERO);
    }

    @Test
    void pushesTheStunnedCombatantsNextTurnBackByItsOwnDelay() {
        CombatResult result = endHeroTurn();

        assertThat(result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(ENEMY))
                .mapToLong(TimelineEntry::readyAtTick)
                .findFirst()
                .orElseThrow()).isEqualTo(150);
    }

    @Test
    void tickstheStunDownWhileTheTurnIsBeingLost() {
        CombatResult result = endHeroTurn();

        CombatantState enemy = result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(ENEMY))
                .findFirst()
                .orElseThrow();
        assertThat(enemy.statuses()).noneMatch(status -> status.statusType().equals("Stun"));
    }

    @Test
    void leavesTheStunnedCombatantWithoutAFreshHand() {
        CombatResult result = endHeroTurn();

        CombatantState enemy = result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(ENEMY))
                .findFirst()
                .orElseThrow();
        assertThat(enemy.piles().hand()).isEmpty();
    }

    private static CombatResult endHeroTurn() {
        CombatState state = state();
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state() {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                0,
                3,
                100,
                new CardPiles(List.of(new CardInstance("spare", "any")), List.of(), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                0,
                2,
                100,
                new CardPiles(List.of(new CardInstance("enemy-card", "any")), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Stun", 0, 1, null, 0)),
                List.of(),
                null,
                0);
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 50, 1)),
                HERO);
    }
}

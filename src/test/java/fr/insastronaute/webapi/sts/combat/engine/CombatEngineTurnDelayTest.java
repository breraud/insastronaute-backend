package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
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
 * How long a combatant waits before acting again.
 *
 * <p>Unity works the wait out through the calculator ({@code Character.cs:347-350}), which is
 * what makes Haste, Slow and Speed mean anything. The engine added the raw authored delay, so the
 * whole tempo family — six statuses and two immunities — was stored, displayed and inert.
 */
class CombatEngineTurnDelayTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void makesAHastenedCombatantWaitLess() {
        CombatResult result = endTurn(new StatusInstance("Haste", 0, 2, null, 0));

        assertThat(waitOf(result, HERO)).isEqualTo(80);
    }

    @Test
    void makesASlowedCombatantWaitLonger() {
        CombatResult result = endTurn(new StatusInstance("Slow", 0, 2, null, 0));

        assertThat(waitOf(result, HERO)).isEqualTo(120);
    }

    @Test
    void leavesThePlainWaitAloneWithoutATempoStatus() {
        CombatResult result = endTurn();

        assertThat(waitOf(result, HERO)).isEqualTo(100);
    }

    private static long waitOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .max()
                .orElseThrow();
    }

    private static CombatResult endTurn(StatusInstance... statuses) {
        CombatState state = state(statuses);
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state(StatusInstance... statuses) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(statuses),
                List.of(),
                null,
                0);
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                500,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 300, 1)),
                HERO);
    }
}

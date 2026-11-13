package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Surcadencage : « Au debut des 2 premiers tours d'un combat, gagnez 1 energie. »
 *
 * <p>Deux tours, donc deux energies en tout - et pas une par tour jusqu'a la fin du combat.</p>
 */
class OverclockRelicHandlerTest {

    private final OverclockRelicHandler handler = new OverclockRelicHandler();

    @Test
    void theFirstTwoTurnsEachGrantOneEnergy() {
        CombatantState owner = combatant();

        CombatantState afterFirst = handler.onTurnStart(state(owner), owner, new ArrayList<>());
        CombatantState afterSecond = handler.onTurnStart(state(afterFirst), afterFirst, new ArrayList<>());

        assertThat(afterFirst.energy()).isEqualTo(4);
        assertThat(afterSecond.energy()).isEqualTo(5);
    }

    @Test
    void theThirdTurnGrantsNothing() {
        CombatantState owner = combatant();

        CombatantState afterFirst = handler.onTurnStart(state(owner), owner, new ArrayList<>());
        CombatantState afterSecond = handler.onTurnStart(state(afterFirst), afterFirst, new ArrayList<>());
        CombatantState afterThird = handler.onTurnStart(state(afterSecond), afterSecond, new ArrayList<>());
        CombatantState afterFourth = handler.onTurnStart(state(afterThird), afterThird, new ArrayList<>());

        assertThat(afterThird.energy()).isEqualTo(5);
        assertThat(afterFourth.energy()).isEqualTo(5);
    }

    private static CombatantState combatant() {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("OverclockRelic"), null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                UUID.randomUUID(),
                12, "v1", CombatStatus.ACTIVE, null,
                new RandomState(123L),
                List.of(combatant, new CombatantState("enemy", "enemy-team", ControllerType.AI,
                        10, 10, 0, 3, 3, 100,
                        new CardPiles(List.of(), List.of(), List.of(), List.of()),
                        List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

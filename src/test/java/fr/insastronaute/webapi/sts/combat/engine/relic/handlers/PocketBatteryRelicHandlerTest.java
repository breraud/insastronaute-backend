package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PocketBatteryRelicHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void testId() {
        PocketBatteryRelicHandler handler = new PocketBatteryRelicHandler();
        assertThat(handler.id()).isEqualTo("PocketBatteryRelic");
    }

    @Test
    void onTurnStartAddsEnergyWhenNotUsed() {
        PocketBatteryRelicHandler handler = new PocketBatteryRelicHandler();

        CombatantState owner = combatant(3, List.of());
        CombatState state = state(owner);

        CombatantState result = handler.onTurnStart(state, owner, new ArrayList<>());

        assertThat(result.energy()).isEqualTo(4);
        assertThat(result.statuses()).hasSize(1);
        StatusInstance status = result.statuses().get(0);
        assertThat(status.statusType()).isEqualTo("PocketBatteryUsed");
        assertThat(status.value()).isEqualTo(1);
    }

    @Test
    void onTurnStartDoesNothingWhenUsed() {
        PocketBatteryRelicHandler handler = new PocketBatteryRelicHandler();

        StatusInstance usedStatus = new StatusInstance("PocketBatteryUsed", 1, 0, "", 0);
        CombatantState owner = combatant(3, List.of(usedStatus));
        CombatState state = state(owner);

        CombatantState result = handler.onTurnStart(state, owner, new ArrayList<>());

        assertThat(result.energy()).isEqualTo(3);
        assertThat(result.statuses()).hasSize(1);
    }

    private static CombatantState combatant(int energy, List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, 0, energy, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of("PocketBatteryRelic"), null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(123L),
                List.of(combatant, new CombatantState("enemy", "enemy-team", ControllerType.AI,
                        10, 10, 0, 3, 3, 100,
                        new CardPiles(List.of(), List.of(), List.of(), List.of()),
                        List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

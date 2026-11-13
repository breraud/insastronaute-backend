package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortableWorkshopRelicTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void testId() {
        PortableWorkshopRelic handler = new PortableWorkshopRelic();
        assertThat(handler.id()).isEqualTo("PortableWorkshopRelic");
    }

    @Test
    void onTurnStartAddsArmorAndStatusWhenNotUsed() {
        PortableWorkshopRelic handler = new PortableWorkshopRelic();

        CombatantState owner = combatant(List.of());
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        CombatantState result = handler.onTurnStart(state, owner, events);

        assertThat(result.armor()).isEqualTo(4);
        assertThat(result.statuses()).hasSize(1);
        assertThat(result.statuses().get(0).statusType()).isEqualTo("PortableWorkshopUsed");
    }

    @Test
    void onTurnStartDoesNothingWhenUsed() {
        PortableWorkshopRelic handler = new PortableWorkshopRelic();

        CombatantState owner = combatant(List.of(new StatusInstance("PortableWorkshopUsed", 1, 0, "", 0)));
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        CombatantState result = handler.onTurnStart(state, owner, events);

        assertThat(result.armor()).isEqualTo(0);
        assertThat(result.statuses()).hasSize(1);
    }

    private static CombatantState combatant(List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 25, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of("PortableWorkshopRelic"), null, 0);
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

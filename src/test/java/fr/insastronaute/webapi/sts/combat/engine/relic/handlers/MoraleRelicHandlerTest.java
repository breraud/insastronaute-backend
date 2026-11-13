package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;

class MoraleRelicHandlerTest {

    @Test
    void onTurnStart_healsIfHpLessThanHalf() {
        MoraleRelicHandler handler = new MoraleRelicHandler();
        CombatantState owner = combatant(10, 30); // 10 < 30/2
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        CombatantState result = handler.onTurnStart(state, owner, events);

        assertThat(result.hp()).isEqualTo(11); // healed by 1
    }

    @Test
    void onTurnStart_doesNotHealIfHpGteHalf() {
        MoraleRelicHandler handler = new MoraleRelicHandler();
        CombatantState owner = combatant(15, 30); // 15 >= 30/2
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        CombatantState result = handler.onTurnStart(state, owner, events);

        assertThat(result.hp()).isEqualTo(15); // no healing
    }

    private static CombatantState combatant(int hp, int maxHp) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                maxHp, hp, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("MoraleRelic"), null, 0);
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

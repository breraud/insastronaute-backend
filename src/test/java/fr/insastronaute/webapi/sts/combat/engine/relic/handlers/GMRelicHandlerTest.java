package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class GMRelicHandlerTest {

    @Test
    void testId() {
        GMRelicHandler handler = new GMRelicHandler();
        assertThat(handler.id()).isNotNull();
    }

    @Test
    void delaysTheBrokenTargetAndKeepsTheTimelineSorted() {
        GMRelicHandler handler = new GMRelicHandler();
        CombatantState source = combatant("hero", "players");
        CombatantState target = combatant("enemy", "enemies");

        List<TimelineEntry> result = handler.onTargetArmorBroken(source, target,
                List.of(new TimelineEntry("enemy", 5, 0), new TimelineEntry("hero", 10, 1)), List.of());

        assertThat(result).containsExactly(
                new TimelineEntry("hero", 10, 1), new TimelineEntry("enemy", 15, 0));
    }

    private static CombatantState combatant(String id, String teamId) {
        return new CombatantState(id, teamId, ControllerType.HUMAN,
                20, 20, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}

package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;

class ReactivePlatingRelicHandlerTest {

    @Test
    void onDamageTaken_addsArmorIfFirstTime() {
        ReactivePlatingRelicHandler handler = new ReactivePlatingRelicHandler();
        CombatantState owner = combatant(0, List.of());
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(1L), UUID.randomUUID(), 0, new ArrayList<>());

        CombatantState updated = result.combatants().get(0);
        assertThat(updated.armor()).isEqualTo(5);
        assertThat(updated.statuses()).anyMatch(s -> s.statusType().equals("ReactivePlatingUsed"));
    }

    @Test
    void onDamageTaken_noArmorIfAlreadyUsed() {
        ReactivePlatingRelicHandler handler = new ReactivePlatingRelicHandler();
        CombatantState owner = combatant(0, List.of(new StatusInstance("ReactivePlatingUsed", 1, -1, "", 0)));
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(1L), UUID.randomUUID(), 0, new ArrayList<>());

        CombatantState updated = result.combatants().get(0);
        assertThat(updated.armor()).isEqualTo(0);
        assertThat(updated.statuses()).hasSize(1);
    }

    private static CombatantState combatant(int armor, List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, armor, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of("ReactivePlatingRelic"), null, 0);
    }
}

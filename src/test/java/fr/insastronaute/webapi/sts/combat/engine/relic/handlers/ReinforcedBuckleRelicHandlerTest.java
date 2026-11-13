package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReinforcedBuckleRelicHandlerTest {

    @Test
    void testId() {
        ReinforcedBuckleRelicHandler handler = new ReinforcedBuckleRelicHandler();
        assertThat(handler.id()).isEqualTo("ReinforcedBuckleRelic");
    }

    @Test
    void onArmorGainAddsArmorAndUsedStatus() {
        ReinforcedBuckleRelicHandler handler = new ReinforcedBuckleRelicHandler();
        CombatantState owner = combatant(10, 0, List.of());
        List<CombatantState> combatants = List.of(owner);
        List<CombatEvent> events = new ArrayList<>();

        List<CombatantState> result = handler.onArmorGain(owner, owner, 5, combatants, events);

        assertThat(result).hasSize(1);
        CombatantState updated = result.get(0);
        assertThat(updated.armor()).isEqualTo(5);
        assertThat(updated.statuses()).hasSize(1);
        assertThat(updated.statuses().get(0).statusType()).isEqualTo("ReinforcedBuckleUsed");
    }

    @Test
    void onArmorGainDoesNothingIfAmountZero() {
        ReinforcedBuckleRelicHandler handler = new ReinforcedBuckleRelicHandler();
        CombatantState owner = combatant(10, 0, List.of());
        List<CombatantState> combatants = List.of(owner);
        List<CombatEvent> events = new ArrayList<>();

        List<CombatantState> result = handler.onArmorGain(owner, owner, 0, combatants, events);

        assertThat(result.get(0).armor()).isEqualTo(0);
        assertThat(result.get(0).statuses()).isEmpty();
    }

    @Test
    void onArmorGainDoesNothingIfAlreadyUsed() {
        ReinforcedBuckleRelicHandler handler = new ReinforcedBuckleRelicHandler();
        CombatantState owner = combatant(10, 0, List.of(new StatusInstance("ReinforcedBuckleUsed", 1, 0, "", 0)));
        List<CombatantState> combatants = List.of(owner);
        List<CombatEvent> events = new ArrayList<>();

        List<CombatantState> result = handler.onArmorGain(owner, owner, 5, combatants, events);

        assertThat(result.get(0).armor()).isEqualTo(0);
        assertThat(result.get(0).statuses()).hasSize(1);
    }

    private static CombatantState combatant(int hp, int armor, List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, hp, armor, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of("ReinforcedBuckleRelic"), null, 0);
    }
}

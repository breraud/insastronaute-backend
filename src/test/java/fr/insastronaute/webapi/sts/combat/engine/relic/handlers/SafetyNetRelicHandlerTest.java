package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafetyNetRelicHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void testId() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        assertThat(handler.id()).isEqualTo("SafetyNetRelic");
    }

    @Test
    void onDamageTakenHealsWhenHpDropsBelowHalf() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        CombatantState owner = combatant(25, 50, List.of()); // hp is maxHp/2
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(123L), COMBAT_ID, 1, new ArrayList<>());

        assertThat(result.combatants()).hasSize(1);
        CombatantState updated = result.combatants().get(0);
        assertThat(updated.hp()).isEqualTo(29); // 25 + 4
        // What the relic remembers is combat state, not a status the player carries.
        assertThat(updated.statuses()).isEmpty();
        assertThat(updated.recall("SafetyNetRelic", "used")).isEqualTo(1);
    }

    @Test
    void onDamageTakenDoesNothingIfHpAboveHalf() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        CombatantState owner = combatant(26, 50, List.of());
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(123L), COMBAT_ID, 1, new ArrayList<>());

        assertThat(result.combatants().get(0).hp()).isEqualTo(26);
        assertThat(result.combatants().get(0).statuses()).isEmpty();
    }
    
    @Test
    void onDamageTakenDoesNothingIfHpZero() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        CombatantState owner = combatant(0, 50, List.of());
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(123L), COMBAT_ID, 1, new ArrayList<>());

        assertThat(result.combatants().get(0).hp()).isEqualTo(0);
    }

    @Test
    void onDamageTakenDoesNothingIfAlreadyUsed() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        CombatantState owner = combatant(25, 50, List.of())
                .remembering("SafetyNetRelic", "used", 1);
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 5, combatants, List.of(), new RandomState(123L), COMBAT_ID, 1, new ArrayList<>());

        assertThat(result.combatants().get(0).hp()).isEqualTo(25);
        assertThat(result.combatants().get(0).recall("SafetyNetRelic", "used")).isEqualTo(1);
    }
    
    @Test
    void onDamageTakenDoesNothingIfAmountZero() {
        SafetyNetRelicHandler handler = new SafetyNetRelicHandler();
        CombatantState owner = combatant(25, 50, List.of());
        List<CombatantState> combatants = List.of(owner);

        ResolutionStep result = handler.onDamageTaken(owner, 0, combatants, List.of(), new RandomState(123L), COMBAT_ID, 1, new ArrayList<>());

        assertThat(result.combatants().get(0).hp()).isEqualTo(25);
    }

    private static CombatantState combatant(int hp, int maxHp, List<StatusInstance> statuses) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                maxHp, hp, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of("SafetyNetRelic"), null, 0);
    }
}

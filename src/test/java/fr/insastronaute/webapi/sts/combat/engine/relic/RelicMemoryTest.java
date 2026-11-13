package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.StatusEngine;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SafetyNetRelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a relic remembers during a combat belongs to the combat, not to a status.
 *
 * <p>A relic that may only fire once per combat used to write a marker status on its owner. That
 * marker was sent to the client as if it were part of the fight, counted in the buff and debuff
 * tallies, and — until it was patched around — could be dispelled or simply expire. The memory
 * now sits in a field of its own on the combatant, which is saved and reloaded with the rest of
 * the state and which no card can reach.
 */
class RelicMemoryTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000131");

    @Test
    void remembersThatARelicHasFiredWithoutPuttingAStatusOnItsOwner() {
        CombatantState afterFirstSave = fire(wounded());

        assertThat(afterFirstSave.statuses()).isEmpty();
        assertThat(afterFirstSave.relicMemory()).isNotEmpty();
    }

    @Test
    void keepsThatMemoryAcrossATurnBoundary() {
        CombatantState afterFirstSave = fire(wounded());

        CombatantState afterATurn = StatusEngine
                .processTurnStart(afterFirstSave, 2L, COMBAT_ID, new RandomState(1))
                .combatant();
        CombatantState afterSecondBlow = fire(afterATurn);

        assertThat(afterFirstSave.hp()).isEqualTo(14);
        assertThat(afterSecondBlow.hp()).isEqualTo(afterATurn.hp());
    }

    private static CombatantState fire(CombatantState owner) {
        ResolutionStep step = new SafetyNetRelicHandler().onDamageTaken(
                owner, 5, List.of(owner), List.of(), new RandomState(1), COMBAT_ID, 1L,
                new ArrayList<>());
        return step.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    private static CombatantState wounded() {
        return new CombatantState(
                "hero",
                "players",
                ControllerType.HUMAN,
                30,
                10,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(),
                List.of("SafetyNetRelic"),
                null,
                0);
    }
}

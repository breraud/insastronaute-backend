package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DamageHandlerNegativeModifierTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @Test
    void clampsDamageToZeroWhenModifiersWouldMakeItNegative() {
        CombatantState source = combatant(
                "hero", 30, 0, List.of(new StatusInstance("Strength", -10, -1, "", 0)));
        CombatantState target = combatant("enemy", 20, 3, List.of());

        ResolutionStep result = new DamageHandler().handle(
                COMBAT_ID,
                8,
                source.combatantId(),
                target,
                new EffectDefinition(EffectType.DAMAGE, 5),
                new RandomState(42),
                List.of(source, target),
                List.of(
                        new TimelineEntry(source.combatantId(), 0, 0),
                        new TimelineEntry(target.combatantId(), 1, 1)),
                null,
                List.of());

        assertThat(result.combatants())
                .filteredOn(combatant -> combatant.combatantId().equals(target.combatantId()))
                .singleElement()
                .satisfies(combatant -> {
                    assertThat(combatant.hp()).isEqualTo(20);
                    assertThat(combatant.armor()).isEqualTo(3);
                });
        assertThat(result.events()).containsExactly(
                new DamageApplied(COMBAT_ID, 8, "hero", "enemy", 0, 0, 0, 3, 20));
    }

    private static CombatantState combatant(
            String id,
            int hp,
            int armor,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                id,
                id + "-team",
                ControllerType.HUMAN,
                30,
                hp,
                armor,
                3,
                3,
                25,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses);
    }
}

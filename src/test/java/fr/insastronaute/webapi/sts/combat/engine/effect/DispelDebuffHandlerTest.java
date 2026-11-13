package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispelDebuffHandlerTest {

    @Test
    void minusOneDispelsAllDebuffsAndPreservesBuffs() {
        CombatantState target = new CombatantState(
                "enemy", "enemy-team", ControllerType.AI, 20, 20, 0, 0, 0, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(
                        new StatusInstance("Weakness", 1, 2, "", 0),
                        new StatusInstance("Poison", 3, 2, "", 0),
                        new StatusInstance("Strength", 2, 0, "", 0)));
        EffectDefinition effect = new EffectDefinition(EffectType.DISPEL_DEBUFF, -1, true);
        CardDefinition card = new CardDefinition(
                "cleanse", 0, TargetingMode.SELF, List.of(effect));

        ResolutionStep result = new DispelDebuffHandler().handle(
                UUID.randomUUID(), 1, "enemy", target, effect, new RandomState(1),
                List.of(target), List.of(), card, List.of());

        assertThat(result.combatants().getFirst().statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("Strength");
    }
}

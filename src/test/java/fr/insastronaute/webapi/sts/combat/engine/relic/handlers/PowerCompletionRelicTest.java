package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PowerCompletionRelicTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final CardCatalog CATALOG = new CardCatalog("v1", Map.of(
            "power-a", power("power-a"),
            "power-b", power("power-b"),
            "skill-a", skill("skill-a")));

    @Test
    void countsOnlyPowersAcrossEveryPileAtCombatStart() {
        PowerCompletionRelic handler = new PowerCompletionRelic();
        CombatantState owner = owner(
                List.of(instance("power-a")), List.of(instance("skill-a")),
                List.of(), List.of(instance("power-b")));

        CombatantState result = handler.onCombatStart(null, owner, CATALOG, new ArrayList<>());

        assertThat(result.recall(handler.id(), "total")).isEqualTo(2);
    }

    @Test
    void grantsNothingUntilEveryPowerInTheDeckHasBeenPlayed() {
        PowerCompletionRelic handler = new PowerCompletionRelic();
        CombatantState owner = handler.onCombatStart(
                null, owner(List.of(instance("power-a")), List.of(), List.of(), List.of(instance("power-b"))),
                CATALOG, new ArrayList<>());

        ResolutionStep step = handler.onCardPlayed(
                owner, CATALOG.require("power-a"), CombatCounters.initial(), List.of(owner),
                List.of(), new RandomState(1), COMBAT_ID, 1, new ArrayList<>());

        CombatantState afterFirstPower = step.combatants().get(0);
        assertThat(afterFirstPower.statuses()).isEmpty();
        assertThat(afterFirstPower.recall(handler.id(), "played")).isEqualTo(1);
    }

    @Test
    void grantsSpeedEqualToTheDeckPowerCountOncePlayedTwice() {
        PowerCompletionRelic handler = new PowerCompletionRelic();
        CombatantState owner = handler.onCombatStart(
                null, owner(List.of(instance("power-a")), List.of(), List.of(), List.of(instance("power-b"))),
                CATALOG, new ArrayList<>());

        ResolutionStep first = handler.onCardPlayed(
                owner, CATALOG.require("power-a"), CombatCounters.initial(), List.of(owner),
                List.of(), new RandomState(1), COMBAT_ID, 1, new ArrayList<>());
        CombatantState afterFirstPower = first.combatants().get(0);

        ResolutionStep second = handler.onCardPlayed(
                afterFirstPower, CATALOG.require("power-b"), CombatCounters.initial(),
                List.of(afterFirstPower), List.of(), new RandomState(1), COMBAT_ID, 2, new ArrayList<>());

        CombatantState afterSecondPower = second.combatants().get(0);
        assertThat(afterSecondPower.statuses()).containsExactly(
                new StatusInstance("Speed", 2, -1, "", 0));
        assertThat(second.events()).anySatisfy(event ->
                assertThat(event).isInstanceOf(StatusApplied.class));

        // A third power played after the relic already fired must not stack a second Speed.
        ResolutionStep third = handler.onCardPlayed(
                afterSecondPower, CATALOG.require("power-a"), CombatCounters.initial(),
                List.of(afterSecondPower), List.of(), new RandomState(1), COMBAT_ID, 3, new ArrayList<>());
        assertThat(third.combatants().get(0).statuses()).containsExactly(
                new StatusInstance("Speed", 2, -1, "", 0));
    }

    private static CombatantState owner(
            List<CardInstance> draw, List<CardInstance> hand,
            List<CardInstance> discard, List<CardInstance> exhaust) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN, 50, 50, 0, 3, 3, 100,
                new CardPiles(draw, hand, discard, exhaust));
    }

    private static CardInstance instance(String definitionId) {
        return new CardInstance(definitionId + "-instance", definitionId);
    }

    private static CardDefinition power(String id) {
        return new CardDefinition(id, 1, CardType.POWER, TargetingMode.SELF, Set.of(), List.of());
    }

    private static CardDefinition skill(String id) {
        return new CardDefinition(id, 1, CardType.SKILL, TargetingMode.SELF, Set.of(), List.of());
    }
}

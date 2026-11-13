package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The same command on the same state produces the same state, byte for byte.
 *
 * <p>Cards an effect creates used to be named with a random UUID, which made every replay differ
 * and defeated snapshot comparison. Every name now comes from the combat state itself.
 */
class CombatDeterminismTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final CardInstance PLAYED = new CardInstance("played", "invocation");

    @Test
    void producesTheSameCreatedCardsWhenTheSameCommandIsReplayed() {
        CombatResult first = play();
        CombatResult second = play();

        assertThat(first.state()).isEqualTo(second.state());
        assertThat(first.events()).isEqualTo(second.events());
    }

    @Test
    void namesEveryCreatedCardDistinctlyWithinOneResolution() {
        CombatantState hero = hero(play().state());

        assertThat(hero.piles().hand()).hasSize(2);
        assertThat(hero.piles().draw()).hasSize(2);
        assertThat(hero.piles().hand().stream().map(CardInstance::instanceId))
                .doesNotContainAnyElementsOf(
                        hero.piles().draw().stream().map(CardInstance::instanceId).toList());
        assertThat(List.of(
                hero.piles().hand().get(0).instanceId(),
                hero.piles().hand().get(1).instanceId(),
                hero.piles().draw().get(0).instanceId(),
                hero.piles().draw().get(1).instanceId()))
                .doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).startsWith("gen-"));
    }

    @Test
    void advancesTheRandomStreamOnceForEachCreatedCard() {
        assertThat(play().state().random()).isNotEqualTo(new RandomState(42));
    }

    private static CombatResult play() {
        CardDefinition invocation = new CardDefinition("invocation", 1, TargetingMode.SELF, List.of(
                new EffectDefinition(EffectType.ADD_CARD_TO_HAND, 2, true, null, 0, null, "rouille"),
                new EffectDefinition(EffectType.ADD_CARD_TO_DRAW_PILE, 2, true, null, 0, null, "rouille")));
        CardCatalog catalog = new CardCatalog("v1", Map.of(
                "invocation", invocation,
                "rouille", new CardDefinition("rouille", 0, TargetingMode.NONE,
                        List.of(new EffectDefinition(EffectType.LOSE_HP, 1, true)))));

        return new CombatEngine(catalog, new RelicEngine(List.of()))
                .handle(state(), new PlayCardCommand(
                        COMBAT_ID, 7, "hero", PLAYED.instanceId(), List.of("hero")));
    }

    private static CombatantState hero(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState state() {
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 20, 20, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

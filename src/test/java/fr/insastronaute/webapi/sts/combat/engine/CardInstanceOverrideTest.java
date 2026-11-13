package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
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
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a card picked up during the fight is played with.
 *
 * <p>A card that was made cheaper, given an extra effect or aimed elsewhere behaves exactly like
 * an authored card that always looked that way.
 */
class CardInstanceOverrideTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f5");

    private static final CardDefinition STRIKE = new CardDefinition(
            "frappe", 2, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
            List.of(new EffectDefinition(EffectType.DAMAGE, 6)));

    @Test
    void playsACardMadeCheaperForLess() {
        CardInstance cheaper = new CardInstance("played", "frappe")
                .withModifier(new CardModifier(StatType.COST, ModifierKind.FLAT, -1, ""));

        CombatResult result = play(cheaper, 2);

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result).energy())
                .describedAs("two energy, one spent")
                .isEqualTo(1);
    }

    @Test
    void playsACardGivenAnExtraEffect() {
        CardInstance reinforced = new CardInstance("played", "frappe")
                .withAddedEffect(new EffectDefinition(EffectType.ARMOR, 4, true));

        CombatResult result = play(reinforced, 3);

        assertThat(hero(result).armor()).isEqualTo(4);
        assertThat(enemy(result).hp()).isEqualTo(24);
    }

    @Test
    void playsACardWhoseAimWasChanged() {
        CardInstance turned = new CardInstance("played", "frappe")
                .withTargetingMode(TargetingMode.SELF);

        CombatResult result = new CombatEngine(catalog(), new RelicEngine(List.of()))
                .handle(state(3, turned), new PlayCardCommand(
                        COMBAT_ID, 2, "hero", "played", List.of("hero")));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result).hp())
                .describedAs("the card now points at its caster")
                .isEqualTo(24);
    }

    @Test
    void keepsACardMarkedToBeKeptWhenTheTurnEnds() {
        CardInstance kept = new CardInstance("played", "frappe").withTag(CardTag.RETAIN);
        CombatState state = state(3, kept, new CardInstance("spare", "frappe"));

        CombatResult result = new CombatEngine(catalog(), new RelicEngine(List.of()))
                .handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), "hero"));

        assertThat(hero(result).piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("played");
        assertThat(hero(result).piles().discard())
                .extracting(CardInstance::instanceId)
                .containsExactly("spare");
    }

    @Test
    void dropsTheBonusesThatOnlyLastedTheTurn() {
        CardInstance boosted = new CardInstance("played", "frappe")
                .withTag(CardTag.RETAIN)
                .withModifier(new CardModifier(StatType.DAMAGE, ModifierKind.FLAT, 5, "", true))
                .withModifier(new CardModifier(StatType.DAMAGE, ModifierKind.FLAT, 3, "", false));
        CombatState state = state(3, boosted);

        CombatResult result = new CombatEngine(catalog(), new RelicEngine(List.of()))
                .handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), "hero"));

        assertThat(hero(result).piles().hand()).singleElement()
                .extracting(CardInstance::modifiers)
                .satisfies(modifiers -> assertThat((List<?>) modifiers).hasSize(1));
    }

    private static CombatResult play(CardInstance card, int energy) {
        return new CombatEngine(catalog(), new RelicEngine(List.of()))
                .handle(state(energy, card), new PlayCardCommand(
                        COMBAT_ID, 2, "hero", "played", List.of("enemy")));
    }

    private static CardCatalog catalog() {
        return new CardCatalog("v1", Map.of("frappe", STRIKE));
    }

    private static CombatantState hero(CombatResult result) {
        return combatant(result, "hero");
    }

    private static CombatantState enemy(CombatResult result) {
        return combatant(result, "enemy");
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState state(int energy, CardInstance... hand) {
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, energy, 3, 10,
                new CardPiles(List.of(), List.of(hand), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 2, "v1", CombatStatus.ACTIVE, null, new RandomState(11),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

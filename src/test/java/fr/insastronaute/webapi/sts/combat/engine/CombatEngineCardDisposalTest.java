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
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.InfinityEnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
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
 * Where a card goes once it has been played.
 *
 * <p>Unity settles this in {@code CombatManager.cs:1643-1660}: a Power leaves play entirely, a
 * card tagged {@code Exhaust} reaches the exhaust pile, and everything else is discarded. The
 * tag is carried by 86 of the 318 authored cards, so sending it to the discard pile hands the
 * player a card the game meant to burn.
 */
class CombatEngineCardDisposalTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final CardInstance PLAYED_CARD = new CardInstance("played", "tested-card");

    @Test
    void sendsACardTaggedExhaustToTheExhaustPileInsteadOfTheDiscardPile() {
        CardDefinition burning = definition(CardType.SKILL, Set.of(CardTag.EXHAUST));

        CombatResult result = play(burning);

        CombatantState hero = hero(result);
        assertThat(hero.piles().exhaust()).extracting(CardInstance::instanceId)
                .containsExactly(PLAYED_CARD.instanceId());
        assertThat(hero.piles().discard()).isEmpty();
        assertThat(hero.piles().hand()).isEmpty();
    }

    @Test
    void keepsAnUntaggedCardOnTheDiscardPile() {
        CardDefinition ordinary = definition(CardType.SKILL, Set.of());

        CombatResult result = play(ordinary);

        CombatantState hero = hero(result);
        assertThat(hero.piles().discard()).extracting(CardInstance::instanceId)
                .containsExactly(PLAYED_CARD.instanceId());
        assertThat(hero.piles().exhaust()).isEmpty();
    }

    @Test
    void keepsAPowerOutOfEveryPileOnceItHasBeenPlayed() {
        CardDefinition power = definition(CardType.POWER, Set.of());

        CombatResult result = play(power);

        CombatantState hero = hero(result);
        assertThat(hero.piles().hand()).isEmpty();
        assertThat(hero.piles().discard()).isEmpty();
        assertThat(hero.piles().exhaust()).isEmpty();
        assertThat(hero.piles().draw()).isEmpty();
    }

    @Test
    void infinityAddsItsTemporaryCostOnlyAfterTheCardReturnsToHand() {
        CardInstance infinity = new CardInstance("played", "tested-card",
                List.of(new EnchantmentInstance("Infinity", 1)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of("tested-card", definition(CardType.SKILL, Set.of()))),
                new RelicEngine(List.of()), new EnchantmentEngine(List.of(new InfinityEnchantmentHandler())));

        CombatResult result = engine.handle(state(infinity), new PlayCardCommand(
                COMBAT_ID, state(infinity).revision(), HERO, infinity.instanceId(), List.of(ENEMY)));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result).piles().hand()).singleElement()
                .extracting(CardInstance::modifiers)
                .isEqualTo(List.of(new CardModifier(StatType.COST, ModifierKind.FLAT, 1, "", true)));
    }

    @Test
    void burnsAPowerThatIsAlsoTaggedExhaustLikeAnyOtherPowerRatherThanExhaustingIt() {
        CardDefinition power = definition(CardType.POWER, Set.of(CardTag.EXHAUST));

        CombatResult result = play(power);

        CombatantState hero = hero(result);
        assertThat(hero.piles().exhaust()).isEmpty();
        assertThat(hero.piles().discard()).isEmpty();
    }

    private static CombatResult play(CardDefinition definition) {
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state(),
                new PlayCardCommand(
                        COMBAT_ID,
                        state().revision(),
                        HERO,
                        PLAYED_CARD.instanceId(),
                        List.of(ENEMY)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CardDefinition definition(CardType type, Set<CardTag> tags) {
        return new CardDefinition(
                "tested-card",
                1,
                type,
                TargetingMode.SINGLE_ENEMY,
                tags,
                List.of(new EffectDefinition(EffectType.DAMAGE, 4, false, null, 0, "", null)));
    }

        private static CombatState state() {
                return state(PLAYED_CARD);
        }

        private static CombatState state(CardInstance playedCard) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                10,
                new CardPiles(List.of(), List.of(playedCard), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)),
                HERO);
    }
}

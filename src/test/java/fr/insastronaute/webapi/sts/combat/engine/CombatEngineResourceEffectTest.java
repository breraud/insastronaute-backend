package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
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

class CombatEngineResourceEffectTest {

    private static final RelicEngine RELIC_ENGINE = new RelicEngine(List.of());
    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final CardInstance PLAYED = card("played", "resource-card");
    private static final CardInstance KEPT = card("kept", "other");
    private static final RandomState RANDOM = new RandomState(42);

    @Test
    void drawsTwoCardsFromIndexZeroToTheEndOfTheHand() {
        CardInstance first = card("draw-1", "other");
        CardInstance second = card("draw-2", "other");
        CardInstance third = card("draw-3", "other");
        CombatState original = state(
                3,
                new CardPiles(
                        List.of(first, second, third),
                        List.of(PLAYED, KEPT),
                        List.of(),
                        List.of()));

        CombatResult result = play(
                original,
                definition(1, effect(EffectType.DRAW, 2)));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().hand()).containsExactly(KEPT, first, second);
        assertThat(hero(result.state()).piles().draw()).containsExactly(third);
        assertThat(hero(result.state()).piles().discard()).containsExactly(PLAYED);
        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 1, 2),
                new CardDrawn(COMBAT_ID, 8, HERO, first.instanceId(), "other", 1),
                new CardDrawn(COMBAT_ID, 8, HERO, second.instanceId(), "other", 2));
        assertThat(original).isEqualTo(state(
                3,
                new CardPiles(
                        List.of(first, second, third),
                        List.of(PLAYED, KEPT),
                        List.of(),
                        List.of())));
    }

    @Test
    void reshufflesDiscardBeforeDrawingAndAdvancesRandomExactly() {
        List<CardInstance> discard = List.of(
                card("discard-1", "other"),
                card("discard-2", "other"),
                card("discard-3", "other"),
                card("discard-4", "other"));
        CombatState original = state(
                3,
                new CardPiles(List.of(), List.of(PLAYED), discard, List.of()));

        CombatResult result = play(
                original,
                definition(0, effect(EffectType.DRAW, 2)));

        List<String> shuffledIds =
                List.of("discard-3", "discard-1", "discard-4", "discard-2");
        assertThat(hero(result.state()).piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("discard-3", "discard-1");
        assertThat(hero(result.state()).piles().draw())
                .extracting(CardInstance::instanceId)
                .containsExactly("discard-4", "discard-2");
        assertThat(hero(result.state()).piles().discard()).containsExactly(PLAYED);
        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 0, 3),
                new PileShuffled(COMBAT_ID, 8, HERO, "DRAW", shuffledIds),
                new CardDrawn(COMBAT_ID, 8, HERO, "discard-3", "other", 0),
                new CardDrawn(COMBAT_ID, 8, HERO, "discard-1", "other", 1));

        RandomState expectedRandom = RANDOM;
        for (int bound : List.of(4, 3, 2)) {
            expectedRandom = expectedRandom.nextInt(bound).next();
        }
        assertThat(result.state().random()).isEqualTo(expectedRandom);
    }

    @Test
    void drawsAcrossTheDrawDiscardBoundaryWithoutReshufflingThePlayedCard() {
        CardInstance lastDraw = card("last-draw", "other");
        CardInstance firstDiscard = card("discard-1", "other");
        CardInstance secondDiscard = card("discard-2", "other");
        CardInstance thirdDiscard = card("discard-3", "other");
        CombatState original = state(
                3,
                new CardPiles(
                        List.of(lastDraw),
                        List.of(PLAYED, KEPT),
                        List.of(firstDiscard, secondDiscard, thirdDiscard),
                        List.of()));

        CombatResult result = play(
                original,
                definition(0, effect(EffectType.DRAW, 2)));

        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 0, 3),
                new CardDrawn(COMBAT_ID, 8, HERO, lastDraw.instanceId(), "other", 1),
                new PileShuffled(
                        COMBAT_ID,
                        8,
                        HERO,
                        "DRAW",
                        List.of(
                                firstDiscard.instanceId(),
                                thirdDiscard.instanceId(),
                                secondDiscard.instanceId())),
                new CardDrawn(COMBAT_ID, 8, HERO, firstDiscard.instanceId(), "other", 2));

        CardPiles resultingPiles = hero(result.state()).piles();
        assertThat(resultingPiles.hand())
                .containsExactly(KEPT, lastDraw, firstDiscard);
        assertThat(resultingPiles.draw())
                .containsExactly(thirdDiscard, secondDiscard);
        assertThat(resultingPiles.discard()).containsExactly(PLAYED);
        assertThat(resultingPiles.exhaust()).isEmpty();
        assertThat(resultingPiles.draw().stream()
                        .map(CardInstance::instanceId)
                        .toList())
                .doesNotContain(PLAYED.instanceId());

        List<String> resultingIds = List.of(
                        resultingPiles.draw(),
                        resultingPiles.hand(),
                        resultingPiles.discard(),
                        resultingPiles.exhaust())
                .stream()
                .flatMap(List::stream)
                .map(CardInstance::instanceId)
                .toList();
        assertThat(resultingIds)
                .containsExactlyInAnyOrder(
                        PLAYED.instanceId(),
                        KEPT.instanceId(),
                        lastDraw.instanceId(),
                        firstDiscard.instanceId(),
                        secondDiscard.instanceId(),
                        thirdDiscard.instanceId())
                .doesNotHaveDuplicates();

        RandomState expectedRandom = RANDOM;
        for (int bound : List.of(3, 2)) {
            expectedRandom = expectedRandom.nextInt(bound).next();
        }
        assertThat(result.state().random()).isEqualTo(expectedRandom);
    }

    @Test
    void stopsDrawingWhenBothSourcePilesAreEmpty() {
        CombatState original = state(
                3,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(0, effect(EffectType.DRAW, 2)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().revision()).isEqualTo(8);
        assertThat(result.state().random()).isEqualTo(RANDOM);
        assertThat(hero(result.state()).piles().hand()).isEmpty();
        assertThat(hero(result.state()).piles().discard()).containsExactly(PLAYED);
        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 0, 3));
    }

    @Test
    void movesASingleDiscardCardWithoutShuffleOrRandomConsumption() {
        CardInstance discarded = card("discarded", "other");
        CombatState original = state(
                3,
                new CardPiles(
                        List.of(),
                        List.of(PLAYED),
                        List.of(discarded),
                        List.of()));

        CombatResult result = play(
                original,
                definition(0, effect(EffectType.DRAW, 1)));

        assertThat(hero(result.state()).piles().hand()).containsExactly(discarded);
        assertThat(hero(result.state()).piles().draw()).isEmpty();
        assertThat(hero(result.state()).piles().discard()).containsExactly(PLAYED);
        assertThat(result.state().random()).isEqualTo(RANDOM);
        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 0, 3),
                new CardDrawn(COMBAT_ID, 8, HERO, discarded.instanceId(), "other", 0));
    }

    @Test
    void gainsEnergyAndReportsTheResult() {
        CombatState original = state(
                3,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(1, effect(EffectType.GAIN_ENERGY, 2)));

        assertThat(hero(result.state()).energy()).isEqualTo(4);
        assertThat(result.events()).containsExactly(
                playedEvent(original),
                new EnergySpent(COMBAT_ID, 8, HERO, 1, 2),
                new EnergyGained(COMBAT_ID, 8, HERO, 2, 4));
    }

    @Test
    void saturatesEnergyAndReportsOnlyTheAmountApplied() {
        CombatState original = state(
                Integer.MAX_VALUE,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(1, effect(EffectType.GAIN_ENERGY, 5)));

        assertThat(hero(result.state()).energy()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.events().get(2))
                .isEqualTo(new EnergyGained(COMBAT_ID, 8, HERO, 1, Integer.MAX_VALUE));
    }

    @Test
    void emitsZeroEnergyGainWhenRequestedAmountIsZero() {
        CombatState original = state(
                3,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(0, effect(EffectType.GAIN_ENERGY, 0)));

        assertThat(result.events().get(2))
                .isEqualTo(new EnergyGained(COMBAT_ID, 8, HERO, 0, 3));
    }

    @Test
    void resolvesDrawThenEnergyGainInDefinitionOrder() {
        CardInstance drawn = card("drawn", "other");
        CombatState original = state(
                3,
                new CardPiles(List.of(drawn), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(
                        1,
                        effect(EffectType.DRAW, 1),
                        effect(EffectType.GAIN_ENERGY, 2)));

        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        CardDrawn.class,
                        EnergyGained.class);
        assertThat(hero(result.state()).piles().hand()).containsExactly(drawn);
        assertThat(hero(result.state()).energy()).isEqualTo(4);
    }

    @Test
    void sameInputAndCommandProduceIdenticalStateAndEvents() {
        CardDefinition definition = definition(
                0,
                effect(EffectType.DRAW, 2),
                effect(EffectType.GAIN_ENERGY, 2));
        CombatState original = state(
                3,
                new CardPiles(
                        List.of(),
                        List.of(PLAYED),
                        List.of(
                                card("one", "other"),
                                card("two", "other"),
                                card("three", "other"),
                                card("four", "other")),
                        List.of()));

        assertThat(play(original, definition)).isEqualTo(play(original, definition));
    }

    @Test
    void damageAndArmorDoNotChangeRandomState() {
        CombatState original = state(
                3,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));

        CombatResult result = play(
                original,
                definition(
                        0,
                        new EffectDefinition(EffectType.DAMAGE, 2, true),
                        new EffectDefinition(EffectType.ARMOR, 2, true)));

        assertThat(result.state().random()).isEqualTo(RANDOM);
        assertThat(result.events())
                .extracting(Object::getClass)
                .containsExactly(
                        CardPlayed.class,
                        EnergySpent.class,
                        DamageApplied.class,
                        ArmorGained.class);
    }

    private static CombatResult play(CombatState state, CardDefinition definition) {
        CombatEngine engine =
                new CombatEngine(new CardCatalog("v1", Map.of(definition.id(), definition)), RELIC_ENGINE);
        return engine.handle(
                state,
                new PlayCardCommand(COMBAT_ID, state.revision(), HERO, PLAYED.instanceId(),
                        List.of(HERO)));
    }

    private static CardDefinition definition(int cost, EffectDefinition... effects) {
        return new CardDefinition(
                PLAYED.definitionId(), cost, TargetingMode.SELF, List.of(effects));
    }

    private static EffectDefinition effect(EffectType type, int value) {
        return new EffectDefinition(type, value);
    }

    private static CombatState state(int energy, CardPiles heroPiles) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                energy,
                3,
                10,
                heroPiles);
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
                RANDOM,
                List.of(hero, enemy),
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ENEMY, 1, 1)),
                HERO);
    }

    private static CombatantState hero(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CardPlayed playedEvent(CombatState original) {
        return new CardPlayed(
                COMBAT_ID,
                original.revision() + 1,
                HERO,
                PLAYED.instanceId(),
                PLAYED.definitionId(),
                List.of(HERO));
    }

    private static CardInstance card(String instanceId, String definitionId) {
        return new CardInstance(instanceId, definitionId);
    }
}

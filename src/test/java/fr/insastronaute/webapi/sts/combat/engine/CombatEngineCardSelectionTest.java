package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardFilterTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionAction;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardEnchanted;
import fr.insastronaute.webapi.sts.combat.event.CardMerged;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CardSelectionResolved;
import fr.insastronaute.webapi.sts.combat.event.CardTransformed;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Card selections that span several piles, filter their candidates, or ask for every eligible
 * card. The state must stay untouched whenever the picks do not satisfy the selection.
 */
class CombatEngineCardSelectionTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    private static final String CHARACTER = "CFI";
    private static final CardInstance PLAYED = new CardInstance("played", "tested-card");
    private static final CardInstance HAND_ATOM = new CardInstance("hand-atom", "atome");
    private static final CardInstance HAND_STRIKE = new CardInstance("hand-strike", "frappe");
    private static final CardInstance DRAW_ATOM = new CardInstance("draw-atom", "atome");
    private static final CardInstance DISCARD_STRIKE = new CardInstance("discard-strike", "frappe");
    private static final CardInstance EXHAUST_ATOM = new CardInstance("exhaust-atom", "atome");

    @Test
    void exhaustsEveryEligibleCardOfEveryPileWhenTheAmountIsMinusOne() {
        CardDefinition fission = selectionCard(
                -1, CardSelectionSource.ALL, CardSelectionAction.EXHAUST, CardFilterTag.ATOM);

        CombatResult result = play(state(), fission,
                List.of("hand-atom", "draw-atom", "exhaust-atom"));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.piles().hand()).containsExactly(HAND_STRIKE);
        assertThat(hero.piles().draw()).isEmpty();
        assertThat(hero.piles().discard()).containsExactly(DISCARD_STRIKE, PLAYED);
        assertThat(hero.piles().exhaust())
                .describedAs("a card already in the destination pile is re-appended in selection order")
                .containsExactly(HAND_ATOM, DRAW_ATOM, EXHAUST_ATOM);
    }

    @Test
    void leavesTheExhaustPileAloneForTheAllExceptExhaustPileSource() {
        CardDefinition card = selectionCard(
                -1, CardSelectionSource.ALL_EXCEPT_EXHAUST_PILE, CardSelectionAction.EXHAUST,
                CardFilterTag.ATOM);

        CombatResult result = play(state(), card, List.of("hand-atom", "draw-atom"));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().exhaust())
                .containsExactly(EXHAUST_ATOM, HAND_ATOM, DRAW_ATOM);
    }

    @Test
    void rejectsASelectionThatSkipsAnEligibleCardAndNamesTheCandidates() {
        CardDefinition fission = selectionCard(
                -1, CardSelectionSource.ALL, CardSelectionAction.EXHAUST, CardFilterTag.ATOM);
        CombatState original = state();

        CombatResult result = play(original, fission, List.of("hand-atom"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection().code()).isEqualTo(RejectionCode.INVALID_CARD_SELECTION);
        assertThat(result.rejection().message())
                .contains("expects 3 card(s)")
                .contains("hand-atom, draw-atom, exhaust-atom");
        assertThat(result.state()).isEqualTo(original);
    }

    /**
     * A selection of every eligible card leaves the player nothing to choose, so a client may
     * name none and let the server take them all -- which is what it has to do when its own
     * filter does not know the tag the card filters on (Inépuisable filters on Exhaust).
     */
    @Test
    void takesEveryEligibleCardWhenTheClientNamesNoneForAnAllSelection() {
        CardDefinition fission = selectionCard(
                -1, CardSelectionSource.ALL, CardSelectionAction.EXHAUST, CardFilterTag.ATOM);

        CombatResult result = play(state(), fission, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().exhaust())
                .containsExactly(HAND_ATOM, DRAW_ATOM, EXHAUST_ATOM);
        assertThat(result.events()).filteredOn(CardSelectionResolved.class::isInstance)
                .singleElement()
                .satisfies(event -> assertThat(
                        ((CardSelectionResolved) event).selectedCardInstanceIds())
                        .containsExactly("hand-atom", "draw-atom", "exhaust-atom"));
    }

    @Test
    void paysOutForEveryCardTheServerTookOnTheClientsBehalf() {
        CardDefinition mailleParfaite = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, -1, true, null, 3, null, null,
                        CardSelectionSource.ALL_EXCEPT_EXHAUST_PILE,
                        CardSelectionAction.CONSUME_AND_GAIN_ARMOR, List.of(CardFilterTag.ATOM))));

        CombatResult result = play(state(), mailleParfaite, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).armor()).isEqualTo(6);
    }

    @Test
    void stillAsksForAChoiceWhenThereIsOneToMake() {
        CardDefinition card = selectionCard(1, CardSelectionSource.HAND, CardSelectionAction.DISCARD);

        CombatResult result = play(state(), card, List.of());

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection().code()).isEqualTo(RejectionCode.INVALID_CARD_SELECTION);
    }

    @Test
    void rejectsACardTheFilterExcludes() {
        CardDefinition fission = selectionCard(
                -1, CardSelectionSource.ALL, CardSelectionAction.EXHAUST, CardFilterTag.ATOM);
        CombatState original = state();

        CombatResult result = play(original, fission,
                List.of("hand-atom", "draw-atom", "hand-strike"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection().code()).isEqualTo(RejectionCode.INVALID_CARD_SELECTION);
        assertThat(result.rejection().message()).contains("not eligible");
        assertThat(result.state()).isEqualTo(original);
    }

        @Test
        void acceptsSelectionsMatchingEitherOfSeveralFilters() {
                CardDefinition card = selectionCard(
                                2, CardSelectionSource.HAND, CardSelectionAction.DISCARD,
                                CardFilterTag.ATOM, CardFilterTag.ATTACK);

                CombatResult result = play(state(), card, List.of("hand-atom", "hand-strike"));

                assertThat(result.accepted()).isTrue();
                assertThat(hero(result.state()).piles().hand()).isEmpty();
                assertThat(hero(result.state()).piles().discard())
                        .containsExactly(DISCARD_STRIKE, HAND_ATOM, HAND_STRIKE, PLAYED);
        }

    @Test
    void rejectsTheSameInstanceSelectedTwice() {
        CardDefinition card = selectionCard(
                2, CardSelectionSource.HAND, CardSelectionAction.DISCARD);
        CombatState original = state();

        CombatResult result = play(original, card, List.of("hand-atom", "hand-atom"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection().code()).isEqualTo(RejectionCode.INVALID_CARD_SELECTION);
        assertThat(result.state()).isEqualTo(original);
    }

    @Test
    void neverLetsACardSelectItself() {
        CardDefinition card = selectionCard(
                -1, CardSelectionSource.HAND, CardSelectionAction.EXHAUST);
        CombatState original = state();

        CombatResult rejected = play(original, card, List.of("played", "hand-atom", "hand-strike"));
        assertThat(rejected.accepted()).isFalse();

        CombatResult accepted = play(original, card, List.of("hand-atom", "hand-strike"));
        assertThat(accepted.accepted()).isTrue();
        assertThat(hero(accepted.state()).piles().discard()).containsExactly(DISCARD_STRIKE, PLAYED);
    }

    @Test
    void resolvesToNothingWhenNoCardIsEligible() {
        CardDefinition card = selectionCard(
                -1, CardSelectionSource.EXHAUST_PILE, CardSelectionAction.RETURN_TO_HAND,
                CardFilterTag.ATTACK);

        CombatResult result = play(state(), card, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().exhaust()).containsExactly(EXHAUST_ATOM);
    }

    @Test
    void putsEachSelectedCardOnTopOfTheDrawPileInSelectionOrder() {
        CardDefinition maquette = selectionCard(
                2, CardSelectionSource.DISCARD_PILE, CardSelectionAction.TOP_OF_DRAW_PILE);
        CombatState original = withDiscard(state(), List.of(DISCARD_STRIKE, HAND_ATOM));

        CombatResult result = play(original, maquette, List.of("discard-strike", "hand-atom"));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().draw())
                .containsExactly(HAND_ATOM, DISCARD_STRIKE, DRAW_ATOM);
    }

    @Test
    void exhaustsTheConsumedCardsAndGrantsArmourPerCard() {
        CardDefinition mailleParfaite = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, -1, true, null, 3, null, null,
                        CardSelectionSource.ALL_EXCEPT_EXHAUST_PILE,
                        CardSelectionAction.CONSUME_AND_GAIN_ARMOR, List.of(CardFilterTag.ATOM))));

        CombatResult result = play(state(), mailleParfaite, List.of("hand-atom", "draw-atom"));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.armor())
                .describedAs("3 armour per consumed card")
                .isEqualTo(6);
        assertThat(hero.piles().exhaust()).containsExactly(EXHAUST_ATOM, HAND_ATOM, DRAW_ATOM);
        assertThat(result.events()).filteredOn(ArmorGained.class::isInstance).hasSize(1);
    }

    @Test
    void grantsNoArmourWhenTheSelectionConsumedNothing() {
        CardDefinition mailleParfaite = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, -1, true, null, 3, null, null,
                        CardSelectionSource.EXHAUST_PILE,
                        CardSelectionAction.CONSUME_AND_GAIN_ARMOR, List.of(CardFilterTag.ATTACK))));

        CombatResult result = play(state(), mailleParfaite, List.of());

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).armor()).isZero();
    }

    @Test
    void transformsACardInPlaceKeepingItsIdentityAndItsPile() {
        CardDefinition convertisseur = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 1, true, null, 0, null, null,
                        CardSelectionSource.DRAW_PILE, CardSelectionAction.TRANSFORM, List.of())));

        CombatResult result = play(state(), convertisseur, List.of("draw-atom"));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.piles().draw()).singleElement().satisfies(card -> {
            assertThat(card.instanceId())
                    .describedAs("the player keeps tracking the same card")
                    .isEqualTo("draw-atom");
            assertThat(card.definitionId()).isEqualTo("creation");
            assertThat(card.enchantments()).isEmpty();
        });
        assertThat(hero.piles().hand()).containsExactly(HAND_ATOM, HAND_STRIKE);
    }

    @Test
    void announcesWhatACardWasTransformedInto() {
        CardDefinition convertisseur = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 1, true, null, 0, null, null,
                        CardSelectionSource.DRAW_PILE, CardSelectionAction.TRANSFORM, List.of())));

        CombatResult result = play(state(), convertisseur, List.of("draw-atom"));

        assertThat(result.events()).filteredOn(CardTransformed.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(CardTransformed.class, event -> {
                    assertThat(event.cardInstanceId()).isEqualTo("draw-atom");
                    assertThat(event.fromDefinitionId()).isEqualTo("atome");
                    assertThat(event.toDefinitionId()).isEqualTo("creation");
                    assertThat(event.pile()).isEqualTo("DRAW");
                });
    }

    @Test
    void dropsTheEnchantmentsOfATransformedCard() {
        CardInstance enchanted = new CardInstance(
                "draw-atom", "atome", List.of(new EnchantmentInstance("Sharpness", 2)));
        CombatState original = withDrawPile(state(), List.of(enchanted));
        CardDefinition convertisseur = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 1, true, null, 0, null, null,
                        CardSelectionSource.DRAW_PILE, CardSelectionAction.TRANSFORM, List.of())));

        CombatResult result = play(original, convertisseur, List.of("draw-atom"));

        assertThat(hero(result.state()).piles().draw()).singleElement()
                .extracting(CardInstance::enchantments)
                .satisfies(enchantments -> assertThat((List<?>) enchantments).isEmpty());
    }

    @Test
    void enchantsACardWhereItLiesAndAnnouncesWhatItGained() {
        CardDefinition resserrage = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 1, true, null, 0, null, null,
                        CardSelectionSource.HAND, CardSelectionAction.ENCHANT,
                        List.of(CardFilterTag.UNUPGRADED))));

        CombatResult result = play(state(), resserrage, List.of("hand-strike"));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("hand-atom", "hand-strike");
        CardInstance enchanted = hero.piles().hand().stream()
                .filter(card -> card.instanceId().equals("hand-strike"))
                .findFirst()
                .orElseThrow();
        assertThat(enchanted.definitionId()).isEqualTo("frappe");
        assertThat(enchanted.enchantments()).isNotEmpty();
        assertThat(result.events()).filteredOn(CardEnchanted.class::isInstance)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(((CardEnchanted) event).cardInstanceId())
                        .isEqualTo("hand-strike"));
    }

    @Test
    void neverOffersAnAlreadyEnchantedCardToAnUnupgradedSelection() {
        CardInstance enchanted = new CardInstance(
                "hand-strike", "frappe", List.of(new EnchantmentInstance("Power", 1)));
        CombatState original = withHand(state(), List.of(PLAYED, HAND_ATOM, enchanted));
        CardDefinition resserrage = new CardDefinition(
                "tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 1, true, null, 0, null, null,
                        CardSelectionSource.HAND, CardSelectionAction.ENCHANT,
                        List.of(CardFilterTag.UNUPGRADED))));

        CombatResult rejected = play(original, resserrage, List.of("hand-strike"));

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.rejection().code()).isEqualTo(RejectionCode.INVALID_CARD_SELECTION);
    }

    @Test
    void foldsTheSelectedCardsIntoOneNewCardInHand() {
        CardDefinition fusion = new CardDefinition(
                "tested-card", 2, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 2, true, null, 0, null, null,
                        CardSelectionSource.HAND, CardSelectionAction.MERGE, List.of())));

        CombatResult result = play(state(), fusion, List.of("hand-atom", "hand-strike"));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.piles().hand())
                .describedAs("the two sources are gone and the merged card took their place")
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.instanceId()).isNotIn("hand-atom", "hand-strike");
                    assertThat(card.definitionId())
                            .describedAs("the merged card is built on the first one selected")
                            .isEqualTo("atome");
                    assertThat(card.displayName()).isEqualTo("atome+frappe");
                });
    }

    @Test
    void givesTheMergedCardTheEffectsAndTheBroadestAimOfItsSources() {
        CardDefinition fusion = new CardDefinition(
                "tested-card", 2, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 2, true, null, 0, null, null,
                        CardSelectionSource.HAND, CardSelectionAction.MERGE, List.of())));

        CombatResult result = play(state(), fusion, List.of("hand-atom", "hand-strike"));

        CardInstance merged = hero(result.state()).piles().hand().getFirst();
        assertThat(merged.targetingMode())
                .describedAs("atome aims at its caster, frappe at an opponent")
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
        assertThat(merged.addedEffects())
                .extracting(EffectDefinition::type)
                .containsExactly(EffectType.DAMAGE);
    }

    @Test
    void copiesTheSelectedCardToHandWithoutMovingOrFlatteningTheOriginal() {
        CardInstance altered = DRAW_ATOM
                .withModifier(new CardModifier(StatType.COST, ModifierKind.FLAT, -1, "", false))
                .withTag(CardTag.EXHAUST);
        CombatState original = withDrawPile(state(), List.of(altered));
        CardDefinition duplicator = selectionCard(
                1, CardSelectionSource.DRAW_PILE, CardSelectionAction.COPY_TO_HAND);

        CombatResult result = play(original, duplicator, List.of(altered.instanceId()));

        assertThat(result.accepted()).isTrue();
        CombatantState hero = hero(result.state());
        assertThat(hero.piles().draw()).containsExactly(altered);
        assertThat(hero.piles().hand()).hasSize(3);
        CardInstance copy = hero.piles().hand().getLast();
        assertThat(copy.instanceId()).isNotEqualTo(altered.instanceId());
        assertThat(copy.definitionId()).isEqualTo(altered.definitionId());
        assertThat(copy.modifiers()).isEqualTo(altered.modifiers());
        assertThat(copy.tags()).isEqualTo(altered.tags());
        assertThat(result.events()).filteredOn(CardMoved.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(CardMoved.class, event -> {
                    assertThat(event.cardInstanceId()).isEqualTo(copy.instanceId());
                    assertThat(event.fromPile()).isEqualTo("NONE");
                    assertThat(event.toPile()).isEqualTo("HAND");
                });
    }

    @Test
    void announcesTheMergeSoAClientCanReplaceTheCards() {
        CardDefinition fusion = new CardDefinition(
                "tested-card", 2, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, 2, true, null, 0, null, null,
                        CardSelectionSource.HAND, CardSelectionAction.MERGE, List.of())));

        CombatResult result = play(state(), fusion, List.of("hand-atom", "hand-strike"));

        assertThat(result.events()).filteredOn(CardMerged.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(CardMerged.class, event -> {
                    assertThat(event.mergedCardInstanceIds())
                            .containsExactly("hand-atom", "hand-strike");
                    assertThat(event.resultingDefinitionId()).isEqualTo("atome");
                });
    }

    @Test
    void reportsOneMovePerCardAndOneResolutionForTheWholeSelection() {
        CardDefinition fission = selectionCard(
                -1, CardSelectionSource.ALL, CardSelectionAction.EXHAUST, CardFilterTag.ATOM);

        CombatResult result = play(state(), fission,
                List.of("hand-atom", "draw-atom", "exhaust-atom"));

        assertThat(result.events()).filteredOn(CardMoved.class::isInstance)
                .extracting(event -> ((CardMoved) event).cardInstanceId())
                .contains("hand-atom", "draw-atom", "exhaust-atom");
        assertThat(result.events()).filteredOn(CardSelectionResolved.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    CardSelectionResolved resolved = (CardSelectionResolved) event;
                    assertThat(resolved.action()).isEqualTo("Exhaust");
                    assertThat(resolved.combatantId()).isEqualTo(HERO);
                    assertThat(resolved.selectedCardInstanceIds())
                            .containsExactly("hand-atom", "draw-atom", "exhaust-atom");
                });
    }

    @Test
    void endingTheTurnClearsTemporaryCostModifiersOutsideTheHand() {
        CardInstance reduced = DISCARD_STRIKE.withModifier(
                new CardModifier(StatType.COST, ModifierKind.FLAT, -1, "", true));
        CombatState original = withDiscard(state(), List.of(reduced));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", CHARACTER, catalogCards()), new RelicEngine(List.of()));

        CombatResult result = engine.handle(original,
                new EndTurnCommand(COMBAT_ID, original.revision(), HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result.state()).piles().discard()).filteredOn(
                card -> card.instanceId().equals(reduced.instanceId()))
                .singleElement()
                .extracting(CardInstance::modifiers)
                .isEqualTo(List.of());
    }

    private static CombatResult play(
            CombatState state,
            CardDefinition definition,
            List<String> selectedCardInstanceIds
    ) {
        Map<String, CardDefinition> cards = new LinkedHashMap<>(catalogCards());
        cards.put(definition.id(), definition);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", CHARACTER, cards), new RelicEngine(List.of()));
        return engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, PLAYED.instanceId(),
                List.of(HERO), selectedCardInstanceIds));
    }

    private static Map<String, CardDefinition> catalogCards() {
        return Map.of(
                "atome", card("atome", 1, CardType.SKILL, CardTag.ATOM),
                "frappe", new CardDefinition("frappe", 1, CardType.ATTACK,
                        TargetingMode.SINGLE_ENEMY, Set.of(),
                        List.of(new EffectDefinition(EffectType.DAMAGE, 6))),
                "creation", new CardDefinition("creation", "Création", CHARACTER, 0, CardType.SKILL,
                        TargetingMode.SELF, Set.of(CardTag.CREATED),
                        List.of(new EffectDefinition(EffectType.DRAW, 1, true))));
    }

    private static CardDefinition card(String id, int cost, CardType type, CardTag... tags) {
        return new CardDefinition(id, cost, type, TargetingMode.SELF, Set.of(tags),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
    }

    private static CardDefinition selectionCard(
            int amount,
            CardSelectionSource source,
            CardSelectionAction action,
            CardFilterTag... filters
    ) {
        return new CardDefinition("tested-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CARD_SELECTION, amount, true, null, 0, null,
                        null, source, action, List.of(filters))));
    }

    private static CombatantState hero(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState withHand(CombatState state, List<CardInstance> hand) {
        CombatantState hero = hero(state);
        return replaceHeroPiles(state, new CardPiles(
                hero.piles().draw(), hand, hero.piles().discard(), hero.piles().exhaust()));
    }

    private static CombatState withDrawPile(CombatState state, List<CardInstance> draw) {
        CombatantState hero = hero(state);
        return replaceHeroPiles(state, new CardPiles(
                draw, hero.piles().hand(), hero.piles().discard(), hero.piles().exhaust()));
    }

    private static CombatState withDiscard(CombatState state, List<CardInstance> discard) {
        CombatantState hero = hero(state);
        return replaceHeroPiles(state, new CardPiles(
                hero.piles().draw(), hero.piles().hand(), discard, hero.piles().exhaust()));
    }

    private static CombatState replaceHeroPiles(CombatState state, CardPiles piles) {
        return new CombatState(
                state.combatId(), state.revision(), state.dataVersion(), state.status(),
                state.winnerTeamId(), state.random(),
                state.combatants().stream()
                        .map(combatant -> combatant.combatantId().equals(HERO)
                                ? withPiles(combatant, piles)
                                : combatant)
                        .toList(),
                state.timeline(), state.activeCombatantId());
    }

    private static CombatantState withPiles(CombatantState combatant, CardPiles piles) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), piles,
                combatant.statuses(), combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex());
    }

    private static CombatState state() {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(
                        List.of(DRAW_ATOM),
                        List.of(PLAYED, HAND_ATOM, HAND_STRIKE),
                        List.of(DISCARD_STRIKE),
                        List.of(EXHAUST_ATOM)));
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 20, 20, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)),
                HERO);
    }
}

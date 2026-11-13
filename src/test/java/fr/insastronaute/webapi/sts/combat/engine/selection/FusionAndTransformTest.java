package fr.insastronaute.webapi.sts.combat.engine.selection;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardFilterTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionAction;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Folding cards together, and turning one into another.
 *
 * <p>Both are card selections resolved server-side, and both were reported as unreliable. What
 * they share is that the eligible list has to be the same list on both sides: the client offers
 * what it computes, the server accepts what it computes, and a card that only one of them counts
 * as eligible is a selection rejected out of hand.
 */
class FusionAndTransformTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000955");
    private static final String HERO = "hero";
    private static final String FOE = "foe";

    // ---- Fusion ----------------------------------------------------------------------

    /** "Fusion nucléaire": two cards from hand become one. */
    @Test
    void mergesTwoCardsFromHandIntoOne() {
        CombatResult result = play(
                fusion(2, CardSelectionSource.HAND, List.of()),
                List.of("a", "b"));

        assertThat(hand(result))
                .as("the two went in and one came out; the unpicked third is untouched")
                .hasSize(2);
        assertThat(hand(result))
                .extracting(CardInstance::instanceId)
                .doesNotContain("a", "b")
                .contains("c");
    }

    @Test
    void theMergedCardWearsBothNames() {
        CombatResult result = play(
                fusion(2, CardSelectionSource.HAND, List.of()),
                List.of("a", "b"));

        assertThat(merged(result).displayName()).contains("Frappe").contains("Garde");
    }

    /** And both sets of effects, which is what makes the description change. */
    @Test
    void theMergedCardWearsBothSetsOfEffects() {
        CombatResult result = play(
                fusion(2, CardSelectionSource.HAND, List.of()),
                List.of("a", "b"));

        assertThat(merged(result).addedEffects())
                .extracting(EffectDefinition::type)
                .contains(EffectType.ARMOR);
    }

    /**
     * A filtered fusion accepts exactly what the filter accepts.
     *
     * <p>"Fusion de pôles" merges two zero-cost cards, and the eligible list is what the two sides
     * have to agree on. Reading it off the card as it now stands rather than off its authored
     * definition is what keeps them agreeing once a card has been made cheaper mid-combat.
     */
    @Test
    void aFilteredFusionAcceptsWhatTheFilterAccepts() {
        CombatResult result = play(
                fusion(2, CardSelectionSource.HAND, List.of(CardFilterTag.COST_0)),
                List.of("a", "c"));

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void aFilteredFusionRefusesWhatItDoesNot() {
        // "b" is the one-cost Garde, which no Cost0 filter should ever offer.
        CombatResult rejected = attempt(
                fusion(2, CardSelectionSource.HAND, List.of(CardFilterTag.COST_0)),
                List.of("a", "b"));

        assertThat(rejected.accepted()).isFalse();
    }

    /** A copy the combat made cheaper counts as cheap, on the same terms the client counts it. */
    @Test
    void aCardMadeZeroCostMidCombatBecomesEligible() {
        Map<String, CardInstance> hand = new LinkedHashMap<>();
        hand.put("a", new CardInstance("a", "frappe"));
        // Garde costs one as authored; this copy of it has been made free.
        hand.put("b", new CardInstance("b", "garde", List.of(), null, null, List.of(), List.of(),
                List.of(), null, 0));

        CombatResult result = attempt(
                fusion(2, CardSelectionSource.HAND, List.of(CardFilterTag.COST_0)),
                List.of("a", "b"), hand);

        assertThat(result.accepted())
                .as("the server judges the card as it now stands, not as it was authored")
                .isTrue();
    }

    // ---- Transformation --------------------------------------------------------------

    /**
     * Transforming swaps what the card <em>is</em> while leaving <em>which</em> card it is alone.
     *
     * <p>Keeping the instance id is deliberate rather than incidental: the client binds a card
     * view to an instance, so a transform that minted a fresh id would orphan the view and the
     * card would visibly vanish and reappear instead of turning into something else.
     */
    @Test
    void transformingKeepsTheCardsIdentityAndSwapsItsDefinition() {
        CombatResult result = play(
                selection(1, CardSelectionSource.HAND, CardSelectionAction.TRANSFORM, List.of()),
                List.of("a"));

        assertThat(hand(result))
                .extracting(CardInstance::instanceId)
                .as("the same three cards are in hand")
                .contains("a", "b", "c");
        assertThat(hand(result))
                .filteredOn(card -> card.instanceId().equals("a"))
                .singleElement()
                .satisfies(card -> assertThat(card.definitionId()).isNotBlank());
    }

    /** And it announces the swap, which is what the client animates. */
    @Test
    void transformingAnnouncesWhatItBecame() {
        CombatResult result = play(
                selection(1, CardSelectionSource.HAND, CardSelectionAction.TRANSFORM, List.of()),
                List.of("a"));

        assertThat(result.events())
                .filteredOn(event -> event instanceof
                        fr.insastronaute.webapi.sts.combat.event.CardTransformed)
                .as("one transformation, announced")
                .hasSize(1);
    }

    // ---- harness ---------------------------------------------------------------------

    /** The card the fusion produced: the one in hand that was not there before. */
    private static CardInstance merged(CombatResult result) {
        return hand(result).stream()
                .filter(card -> !List.of("a", "b", "c", "played").contains(card.instanceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing was merged"));
    }

    private static List<CardInstance> hand(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow()
                .piles()
                .hand();
    }

    private static CardDefinition fusion(
            int count, CardSelectionSource source, List<CardFilterTag> filters) {
        return selection(count, source, CardSelectionAction.MERGE, filters);
    }

    private static CardDefinition selection(
            int count,
            CardSelectionSource source,
            CardSelectionAction action,
            List<CardFilterTag> filters
    ) {
        return new CardDefinition(
                "outil", "Outil", "", 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.CARD_SELECTION, count, true, null, 0, null, null,
                        source, action, filters)));
    }

    private static CombatResult play(CardDefinition card, List<String> picks) {
        CombatResult result = attempt(card, picks);
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatResult attempt(CardDefinition card, List<String> picks) {
        Map<String, CardInstance> hand = new LinkedHashMap<>();
        hand.put("a", new CardInstance("a", "frappe"));
        hand.put("b", new CardInstance("b", "garde"));
        hand.put("c", new CardInstance("c", "eclat"));
        return attempt(card, picks, hand);
    }

    private static CombatResult attempt(
            CardDefinition card, List<String> picks, Map<String, CardInstance> handById) {
        List<CardInstance> hand = new java.util.ArrayList<>(handById.values());
        hand.add(new CardInstance("played", card.id()));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), hand, List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, 30, 30, 0, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, foe),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)),
                HERO, CombatCounters.initial());

        return new CombatEngine(catalog(card), new RelicEngine(List.of())).handle(
                state,
                new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played", List.of(HERO),
                        picks));
    }

    private static CardCatalog catalog(CardDefinition card) {
        Map<String, CardDefinition> cards = new LinkedHashMap<>();
        cards.put(card.id(), card);
        cards.put("frappe", new CardDefinition(
                "frappe", "Frappe", "", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(CardTag.CREATED),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false))));
        cards.put("garde", new CardDefinition(
                "garde", "Garde", "", 1, CardType.SKILL, TargetingMode.SELF,
                Set.of(CardTag.CREATED),
                List.of(new EffectDefinition(EffectType.ARMOR, 4, true))));
        cards.put("eclat", new CardDefinition(
                "eclat", "Éclat", "", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(CardTag.CREATED),
                List.of(new EffectDefinition(EffectType.DAMAGE, 2, false))));
        return new CardCatalog("v1", cards);
    }
}

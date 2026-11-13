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
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CardSelectionResolverTest {

    private static final CardCatalog CATALOG = new CardCatalog("v1", Map.of(
            "atome", card("atome", 1, CardType.SKILL, CardTag.ATOM),
            "molecule", card("molecule", 2, CardType.SKILL, CardTag.MOLECULE),
            "frappe", card("frappe", 1, CardType.ATTACK),
            "surchauffe", card("surchauffe", 3, CardType.POWER)));

    private final CardSelectionResolver resolver = new CardSelectionResolver(CATALOG);

    private static final CardInstance HAND_ATOM = new CardInstance("hand-atom", "atome");
    private static final CardInstance HAND_STRIKE = new CardInstance("hand-strike", "frappe");
    private static final CardInstance DRAW_ATOM = new CardInstance("draw-atom", "atome");
    private static final CardInstance DISCARD_MOLECULE = new CardInstance("discard-molecule", "molecule");
    private static final CardInstance EXHAUST_ATOM = new CardInstance("exhaust-atom", "atome");

    private static final CardPiles PILES = new CardPiles(
            List.of(DRAW_ATOM),
            List.of(HAND_ATOM, HAND_STRIKE),
            List.of(DISCARD_MOLECULE),
            List.of(EXHAUST_ATOM));

    @Test
    void readsASinglePileInItsOwnOrder() {
        assertThat(instanceIds(selection(1, CardSelectionSource.HAND)))
                .containsExactly("hand-atom", "hand-strike");
    }

    @Test
    void readsEveryPileInAStableOrderForTheAllSource() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL)))
                .containsExactly("hand-atom", "hand-strike", "discard-molecule", "draw-atom", "exhaust-atom");
    }

    @Test
    void leavesExhaustedCardsOutOfTheAllExceptExhaustPileSource() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL_EXCEPT_EXHAUST_PILE)))
                .containsExactly("hand-atom", "hand-strike", "discard-molecule", "draw-atom");
    }

    @Test
    void keepsTheOriginPileOfEachCandidateSoCombinedSourcesCanBeMoved() {
        assertThat(resolver.candidates(selection(-1, CardSelectionSource.ALL), PILES, null))
                .extracting(CardSelectionCandidate::pile)
                .containsExactly(
                        SelectionPile.HAND, SelectionPile.HAND,
                        SelectionPile.DISCARD, SelectionPile.DRAW, SelectionPile.EXHAUST);
    }

    @Test
    void appliesTheAuthoredTagFilterAcrossEveryPile() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.ATOM)))
                .containsExactly("hand-atom", "draw-atom", "exhaust-atom");
    }

    @Test
    void filtersOnTheCardFamily() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.ATTACK)))
                .containsExactly("hand-strike");
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.SKILL)))
                .containsExactly("hand-atom", "discard-molecule", "draw-atom", "exhaust-atom");
    }

    @Test
    void filtersOnTheAuthoredCost() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.COST_1)))
                .containsExactly("hand-atom", "hand-strike", "draw-atom", "exhaust-atom");
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.COST_2)))
                .containsExactly("discard-molecule");
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.COST_0)))
                .isEmpty();
    }

    @Test
    void acceptsACardAsSoonAsOneFilterMatches() {
        assertThat(instanceIds(selection(
                -1, CardSelectionSource.ALL, CardFilterTag.ATTACK, CardFilterTag.MOLECULE)))
                .describedAs("filters are a union, not an intersection")
                .containsExactly("hand-strike", "discard-molecule");
    }

    @Test
    void matchesNothingForAFilterUnityNeverEvaluates() {
        assertThat(instanceIds(selection(-1, CardSelectionSource.ALL, CardFilterTag.EXHAUSTED)))
                .isEmpty();
    }

    @Test
    void tellsUpgradedInstancesApartByTheirEnchantments() {
        CardInstance enchanted = new CardInstance(
                "hand-enchanted", "frappe", List.of(new EnchantmentInstance("Sharpness", 1)));
        CardPiles piles = new CardPiles(List.of(), List.of(HAND_ATOM, enchanted), List.of(), List.of());

        assertThat(instanceIds(selection(-1, CardSelectionSource.HAND, CardFilterTag.UPGRADED), piles))
                .containsExactly("hand-enchanted");
        assertThat(instanceIds(selection(-1, CardSelectionSource.HAND, CardFilterTag.UNUPGRADED), piles))
                .containsExactly("hand-atom");
    }

    @Test
    void neverOffersTheCardBeingPlayedAsACandidateOfItsOwnSelection() {
        assertThat(resolver.candidates(selection(-1, CardSelectionSource.HAND), PILES, "hand-atom"))
                .extracting(CardSelectionCandidate::instanceId)
                .containsExactly("hand-strike");
    }

    @Test
    void asksForEveryEligibleCardWhenTheAmountIsMinusOne() {
        List<CardSelectionCandidate> candidates =
                resolver.candidates(selection(-1, CardSelectionSource.ALL, CardFilterTag.ATOM), PILES, null);

        assertThat(CardSelectionResolver.requiredCount(
                selection(-1, CardSelectionSource.ALL, CardFilterTag.ATOM), candidates.size()))
                .isEqualTo(3);
    }

    @Test
    void neverAsksForMoreCardsThanAreAvailable() {
        assertThat(CardSelectionResolver.requiredCount(selection(5, CardSelectionSource.HAND), 2))
                .isEqualTo(2);
        assertThat(CardSelectionResolver.requiredCount(selection(1, CardSelectionSource.HAND), 2))
                .isEqualTo(1);
    }

    private List<String> instanceIds(EffectDefinition effect) {
        return instanceIds(effect, PILES);
    }

    private List<String> instanceIds(EffectDefinition effect, CardPiles piles) {
        return resolver.candidates(effect, piles, null).stream()
                .map(CardSelectionCandidate::instanceId)
                .toList();
    }

    private static EffectDefinition selection(
            int value,
            CardSelectionSource source,
            CardFilterTag... filters
    ) {
        return new EffectDefinition(EffectType.CARD_SELECTION, value, true, null, 0, null, null,
                source, CardSelectionAction.EXHAUST, List.of(filters));
    }

    private static CardDefinition card(String id, int cost, CardType type, CardTag... tags) {
        return new CardDefinition(id, cost, type, TargetingMode.SELF, Set.of(tags),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
    }
}

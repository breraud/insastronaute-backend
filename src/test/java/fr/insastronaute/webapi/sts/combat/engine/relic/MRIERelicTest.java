package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MRIERelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MRIE, "Coeur galactique": "ajoute 1 Météorite à votre pioche et une Galaxie à votre défausse."
 *
 * <p>The piles it puts them in are the ones its own text names, and the ids it writes on them are
 * the catalog's. That second half is the one that broke: the relic wrote the cards' <em>display
 * names</em> as their {@code definitionId}. The catalog resolves those -- it indexes by name as
 * well as by id -- so nothing failed server-side and the mistake stayed invisible here.
 *
 * <p>It was not invisible in the client, which compares the {@code definitionId} it is sent
 * against a card's own {@code id} with an ordinal equality, to decide whether it already holds
 * that card. "Météorite" never equals "meteorite", so these two cards, alone among the deck, were
 * rebuilt from scratch on every single state sync, and the views bound to the instances before
 * them were orphaned.
 */
class MRIERelicTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000416");

    @Test
    void writesTheCatalogIdOnTheCardsItGenerates() {
        CardCatalog catalog = catalog();
        CombatantState hero = openWithMrie(catalog);

        List<String> generated = new ArrayList<>();
        hero.piles().draw().forEach(card -> generated.add(card.definitionId()));
        hero.piles().discard().forEach(card -> generated.add(card.definitionId()));

        assertThat(generated)
                .as("both cards reach a pile at all")
                .contains("meteorite", "galaxie");
    }

    /**
     * The invariant behind that, stated so it holds for whatever the relic generates next: a
     * reference the catalog resolves is not enough, it has to be the canonical id. The catalog
     * answers to a display name too, which is exactly why the defect survived.
     */
    @Test
    void neverWritesAnAliasTheCatalogMerelyHappensToResolve() {
        CardCatalog catalog = catalog();
        CombatantState hero = openWithMrie(catalog);

        List<CardInstance> generated = new ArrayList<>();
        hero.piles().draw().stream()
                .filter(card -> card.instanceId().startsWith("gen-MRIERelic"))
                .forEach(generated::add);
        hero.piles().discard().stream()
                .filter(card -> card.instanceId().startsWith("gen-MRIERelic"))
                .forEach(generated::add);

        assertThat(generated).hasSize(2);
        assertThat(generated).allSatisfy(card -> {
            CardDefinition definition = catalog.find(card.definitionId());
            assertThat(definition)
                    .as("%s resolves at all", card.definitionId())
                    .isNotNull();
            assertThat(card.definitionId())
                    .as("the client compares this against the card's own id, ordinally: "
                            + "an alias the catalog resolves is still a mismatch there")
                    .isEqualTo(definition.id());
        });
    }

    @Test
    void putsTheMeteoriteInTheDrawPileAndTheGalaxieInTheDiscard() {
        CombatantState hero = openWithMrie(catalog());

        assertThat(hero.piles().draw()).anyMatch(card -> card.definitionId().equals("meteorite"));
        assertThat(hero.piles().discard()).anyMatch(card -> card.definitionId().equals("galaxie"));
        assertThat(hero.piles().hand())
                .as("neither is dealt: the relic names the draw and the discard, not the hand")
                .noneMatch(card -> card.instanceId().startsWith("gen-MRIERelic"));
    }

    /**
     * The declaration and the deed, pinned to each other.
     *
     * <p>A combat's catalog holds what the combat names up front, and a card a relic invents is
     * named by no deck and no enemy move. MRIE's two cards reached the piles with no definition
     * behind them, so playing either was refused with UNSUPPORTED_EFFECT -- the catalog simply had
     * never heard of them. {@code generatedCardReferences} is what puts them in it, and it is only
     * worth anything while it says exactly what the relic writes.
     */
    @Test
    void declaresExactlyTheCardsItCreates() {
        CombatantState hero = openWithMrie(catalog());

        List<String> created = new ArrayList<>();
        hero.piles().draw().stream()
                .filter(card -> card.instanceId().startsWith("gen-MRIERelic"))
                .forEach(card -> created.add(card.definitionId()));
        hero.piles().discard().stream()
                .filter(card -> card.instanceId().startsWith("gen-MRIERelic"))
                .forEach(card -> created.add(card.definitionId()));

        assertThat(new MRIERelicHandler().generatedCardReferences())
                .as("what the relic declares must be what it writes, or the catalog is built "
                        + "without the very card the relic is about to deal")
                .containsExactlyInAnyOrderElementsOf(created);
    }

    /** And the engine hands them on for the relic ids a run actually carries. */
    @Test
    void theEngineCollectsThemForARunHoldingTheRelic() {
        RelicEngine engine = new RelicEngine(List.of(new MRIERelicHandler()));

        assertThat(engine.generatedCardReferences(List.of("MRIERelic")))
                .containsExactlyInAnyOrder("meteorite", "galaxie");
        assertThat(engine.generatedCardReferences(List.of("SomeRelicNobodyImplemented")))
                .as("an unknown relic contributes nothing rather than exploding")
                .isEmpty();
        assertThat(engine.generatedCardReferences(null)).isEmpty();
    }

    // ------------------------------------------------------------------------ fixtures

    private static CombatantState openWithMrie(CardCatalog catalog) {
        CombatFactory factory =
                new CombatFactory(catalog, new RelicEngine(List.of(new MRIERelicHandler())));

        CombatantSetup hero = new CombatantSetup(
                "hero", "players", ControllerType.HUMAN, 80, 3, 10, 0,
                deck(), List.of("MRIERelic"));
        CombatantSetup enemy = new CombatantSetup(
                "enemy", "enemies", ControllerType.AI, 30, 0, 12, 100, List.of());

        CombatState state =
                factory.create(new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, enemy))).state();
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    /** Shaped like the real data: a lower-case id, an accented display name beside it. */
    private static CardCatalog catalog() {
        Map<String, CardDefinition> cards = new LinkedHashMap<>();
        cards.put("strike", card("strike", "Frappe"));
        cards.put("meteorite", card("meteorite", "Météorite"));
        cards.put("galaxie", card("galaxie", "Galaxie"));
        return new CardCatalog("v1", cards);
    }

    private static CardDefinition card(String id, String name) {
        return new CardDefinition(
                id, name, "Aucun", 1, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                java.util.Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, "", null)));
    }

    private static List<CardInstance> deck() {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            deck.add(new CardInstance("hero-" + index, "strike"));
        }
        return deck;
    }
}

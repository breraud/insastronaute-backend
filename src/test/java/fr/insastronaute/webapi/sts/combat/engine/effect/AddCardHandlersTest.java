package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.EffectResolver;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddCardHandlersTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long REVISION = 42L;
    private static final String ACTOR_ID = "hero";
    private static final RandomState RANDOM = new RandomState(12345);

    private CombatantState hero;
    private CardDefinition currentCard;

    @BeforeEach
    void setUp() {
        CardPiles piles = new CardPiles(
                List.of(new CardInstance("draw-1", "strike")),
                List.of(new CardInstance("hand-1", "defend")),
                List.of(new CardInstance("discard-1", "strike")),
                List.of()
        );
        hero = new CombatantState(
                ACTOR_ID,
                "team-hero",
                ControllerType.HUMAN,
                50,
                50,
                0,
                3,
                3,
                10,
                piles,
                List.of(),
                java.util.Collections.emptyList()
        );
        currentCard = new CardDefinition(
                "tested-card",
                1,
                TargetingMode.SELF,
                List.of(new EffectDefinition(EffectType.ADD_CARD_TO_HAND, 1))
        );
    }

    @Test
    void addCardToHandHandlerAddsCardToHandAndEmitsCardMovedEvent() {
        AddCardToHandHandler handler = new AddCardToHandHandler();
        EffectDefinition effect = new EffectDefinition(
                EffectType.ADD_CARD_TO_HAND,
                1,
                true,
                null,
                0,
                null,
                "special-card"
        );

        ResolutionStep step = handler.handle(
                COMBAT_ID,
                REVISION,
                ACTOR_ID,
                hero,
                effect,
                RANDOM,
                List.of(hero),
                List.of(),
                currentCard,
                java.util.Collections.emptyList()
        );

        assertThat(step.combatants().get(0).piles().hand()).hasSize(2);
        CardInstance addedCard = step.combatants().get(0).piles().hand().get(1);
        assertThat(addedCard.definitionId()).isEqualTo("special-card");
        assertThat(addedCard.instanceId()).isNotBlank();

        assertThat(step.combatants().get(0).piles().draw()).hasSize(1);
        assertThat(step.combatants().get(0).piles().discard()).hasSize(1);

        assertThat(step.events()).hasSize(1);
        assertThat(step.events().get(0)).isInstanceOf(CardMoved.class);
        CardMoved event = (CardMoved) step.events().get(0);
        assertThat(event.combatId()).isEqualTo(COMBAT_ID);
        assertThat(event.revision()).isEqualTo(REVISION);
        assertThat(event.combatantId()).isEqualTo(ACTOR_ID);
        assertThat(event.cardInstanceId()).isEqualTo(addedCard.instanceId());
        assertThat(event.fromPile()).isEqualTo("NONE");
        assertThat(event.toPile()).isEqualTo("HAND");
        assertThat(event.destinationIndex()).isEqualTo(1);
    }

    @Test
    void addCardToDrawPileHandlerAddsCardToDrawAndEmitsCardMovedEvent() {
        AddCardToDrawPileHandler handler = new AddCardToDrawPileHandler();
        EffectDefinition effect = new EffectDefinition(
                EffectType.ADD_CARD_TO_DRAW_PILE,
                1,
                true,
                null,
                0,
                null,
                "draw-extra-card"
        );

        ResolutionStep step = handler.handle(
                COMBAT_ID,
                REVISION,
                ACTOR_ID,
                hero,
                effect,
                RANDOM,
                List.of(hero),
                List.of(),
                currentCard,
                java.util.Collections.emptyList()
        );

        assertThat(step.combatants().get(0).piles().draw()).hasSize(2);
        CardInstance addedCard = step.combatants().get(0).piles().draw().get(1);
        assertThat(addedCard.definitionId()).isEqualTo("draw-extra-card");
        assertThat(addedCard.instanceId()).isNotBlank();

        assertThat(step.combatants().get(0).piles().hand()).hasSize(1);
        assertThat(step.combatants().get(0).piles().discard()).hasSize(1);

        assertThat(step.events()).hasSize(1);
        CardMoved event = (CardMoved) step.events().get(0);
        assertThat(event.combatId()).isEqualTo(COMBAT_ID);
        assertThat(event.revision()).isEqualTo(REVISION);
        assertThat(event.combatantId()).isEqualTo(ACTOR_ID);
        assertThat(event.cardInstanceId()).isEqualTo(addedCard.instanceId());
        assertThat(event.fromPile()).isEqualTo("NONE");
        assertThat(event.toPile()).isEqualTo("DRAW");
        assertThat(event.destinationIndex()).isEqualTo(1);
    }

    @Test
    void addCardToDiscardPileHandlerAddsCardToDiscardAndEmitsCardMovedEvent() {
        AddCardToDiscardPileHandler handler = new AddCardToDiscardPileHandler();
        EffectDefinition effect = new EffectDefinition(
                EffectType.ADD_CARD_TO_DISCARD_PILE,
                1,
                true,
                null,
                0,
                null,
                "discard-extra-card"
        );

        ResolutionStep step = handler.handle(
                COMBAT_ID,
                REVISION,
                ACTOR_ID,
                hero,
                effect,
                RANDOM,
                List.of(hero),
                List.of(),
                currentCard,
                java.util.Collections.emptyList()
        );

        assertThat(step.combatants().get(0).piles().discard()).hasSize(2);
        CardInstance addedCard = step.combatants().get(0).piles().discard().get(1);
        assertThat(addedCard.definitionId()).isEqualTo("discard-extra-card");
        assertThat(addedCard.instanceId()).isNotBlank();

        assertThat(step.combatants().get(0).piles().hand()).hasSize(1);
        assertThat(step.combatants().get(0).piles().draw()).hasSize(1);

        assertThat(step.events()).hasSize(1);
        CardMoved event = (CardMoved) step.events().get(0);
        assertThat(event.combatId()).isEqualTo(COMBAT_ID);
        assertThat(event.revision()).isEqualTo(REVISION);
        assertThat(event.combatantId()).isEqualTo(ACTOR_ID);
        assertThat(event.cardInstanceId()).isEqualTo(addedCard.instanceId());
        assertThat(event.fromPile()).isEqualTo("NONE");
        assertThat(event.toPile()).isEqualTo("DISCARD");
        assertThat(event.destinationIndex()).isEqualTo(1);
    }

    @Test
    void addCopyOfCardHandlerAddsCopyOfCurrentCardToHand() {
        AddCopyOfCardHandler handler = new AddCopyOfCardHandler();
        EffectDefinition effect = new EffectDefinition(
                EffectType.ADD_COPY_OF_CARD,
                1,
                true
        );

        ResolutionStep step = handler.handle(
                COMBAT_ID,
                REVISION,
                ACTOR_ID,
                hero,
                effect,
                RANDOM,
                List.of(hero),
                List.of(),
                currentCard,
                java.util.Collections.emptyList()
        );

        assertThat(step.combatants().get(0).piles().hand()).hasSize(2);
        CardInstance addedCard = step.combatants().get(0).piles().hand().get(1);
        assertThat(addedCard.definitionId()).isEqualTo("tested-card");
        assertThat(addedCard.instanceId()).isNotBlank();

        assertThat(step.events()).hasSize(1);
        CardMoved event = (CardMoved) step.events().get(0);
        assertThat(event.combatId()).isEqualTo(COMBAT_ID);
        assertThat(event.revision()).isEqualTo(REVISION);
        assertThat(event.combatantId()).isEqualTo(ACTOR_ID);
        assertThat(event.cardInstanceId()).isEqualTo(addedCard.instanceId());
        assertThat(event.fromPile()).isEqualTo("NONE");
        assertThat(event.toPile()).isEqualTo("HAND");
        assertThat(event.destinationIndex()).isEqualTo(1);
    }

    @Test
    void effectResolverResolvesAllNewEffectTypes() {
        CardCatalog catalog = new CardCatalog("v1", java.util.Map.of("tested-card", currentCard));
        EffectResolver resolver = new EffectResolver(catalog, new RelicEngine(List.of()));

        CardDefinition cardWithAllNewEffects = new CardDefinition(
                "tested-card-all",
                1,
                TargetingMode.SELF,
                List.of(
                        new EffectDefinition(EffectType.ADD_CARD_TO_HAND, 1, true, null, 0, null, "card-a"),
                        new EffectDefinition(EffectType.ADD_CARD_TO_DRAW_PILE, 1, true, null, 0, null, "card-b"),
                        new EffectDefinition(EffectType.ADD_CARD_TO_DISCARD_PILE, 1, true, null, 0, null, "card-c"),
                        new EffectDefinition(EffectType.ADD_COPY_OF_CARD, 1, true)
                )
        );

        EffectResolver.Resolution resolution = resolver.resolve(
                COMBAT_ID,
                REVISION,
                List.of(hero),
                List.of(),
                RANDOM,
                ACTOR_ID,
                List.of(ACTOR_ID),
                cardWithAllNewEffects,
                List.of()
        );

        CombatantState resolvedHero = resolution.combatants().get(0);
        assertThat(resolvedHero.piles().hand()).hasSize(3); // original 1 + card-a + multi-spawn-card
        assertThat(resolvedHero.piles().draw()).hasSize(2); // original 1 + card-b
        assertThat(resolvedHero.piles().discard()).hasSize(2); // original 1 + card-c
        assertThat(resolution.events()).hasSize(4);
    }

    // --- AddCopyOfCard : la copie va dans la pile qu'on lui a écrite ---------------------------

    /**
     * Une copie authored vers la défausse ne doit pas atterrir en main : sur une carte gratuite,
     * c'est la différence entre un effet et une boucle sans fin.
     */
    @Test
    void addCopyOfCardHonoursTheAuthoredDestinationPile() {
        assertCopyLandsIn(CardSelectionSource.DISCARD_PILE, "DISCARD");
        assertCopyLandsIn(CardSelectionSource.DRAW_PILE, "DRAW");
        assertCopyLandsIn(CardSelectionSource.EXHAUST_PILE, "EXHAUST");
        assertCopyLandsIn(CardSelectionSource.HAND, "HAND");
    }

    /** Les unions nomment où lire, pas où poser : elles retombent en main, comme chez Unity. */
    @Test
    void addCopyOfCardFallsBackToHandForUnionsAndForAnUnauthoredSource() {
        assertCopyLandsIn(CardSelectionSource.ALL, "HAND");
        assertCopyLandsIn(CardSelectionSource.ALL_EXCEPT_EXHAUST_PILE, "HAND");
        assertCopyLandsIn(null, "HAND");
    }

    private void assertCopyLandsIn(CardSelectionSource source, String expectedPile) {
        EffectDefinition effect = new EffectDefinition(
                EffectType.ADD_COPY_OF_CARD, 1, true, null, 0, null, null,
                source, null, List.of());

        ResolutionStep step = new AddCopyOfCardHandler().handle(
                COMBAT_ID, REVISION, ACTOR_ID, hero, effect, RANDOM,
                List.of(hero), List.of(), currentCard, java.util.Collections.emptyList());

        CardPiles piles = step.combatants().get(0).piles();
        assertThat(pile(piles, expectedPile))
                .describedAs("copie posée dans %s", expectedPile)
                .extracting(CardInstance::definitionId)
                .contains(currentCard.id());
        assertThat(((CardMoved) step.events().get(0)).toPile()).isEqualTo(expectedPile);

        // Les trois autres piles gardent exactement ce qu'elles avaient.
        for (String other : List.of("HAND", "DRAW", "DISCARD", "EXHAUST")) {
            if (!other.equals(expectedPile)) {
                assertThat(pile(piles, other)).isEqualTo(pile(hero.piles(), other));
            }
        }
    }

    private static List<CardInstance> pile(CardPiles piles, String name) {
        return switch (name) {
            case "DRAW" -> piles.draw();
            case "DISCARD" -> piles.discard();
            case "EXHAUST" -> piles.exhaust();
            default -> piles.hand();
        };
    }
}

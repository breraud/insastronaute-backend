package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Putting a card into somebody else's deck.
 *
 * <p>In a run these effects only ever pointed at their caster: an enemy has no hand to receive a
 * card, and Unity's own resolver refuses a non-player target outright. A duel is the first place
 * where the other side is a real deck with real piles, so an effect authored to give the opponent
 * something is only meaningful there.
 */
class AddCardToOpponentTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000611");
    private static final String HERO = "hero";
    private static final String RIVAL = "rival";

    /** The junk being handed over: a card no deck contains, only an effect names. */
    private static CardDefinition burden() {
        return new CardDefinition(
                "fardeau", "Fardeau", "", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
    }

    private static CardDefinition giving(EffectType type) {
        return new CardDefinition(
                "don", "Don empoisonné", "", 0, CardType.SKILL, TargetingMode.SINGLE_ENEMY,
                Set.of(),
                List.of(new EffectDefinition(
                        type, 1, false, null, 0, null, "fardeau")));
    }

    @Test
    void addsToTheOpponentsHandRatherThanTheCastersOwn() {
        CombatResult result = play(giving(EffectType.ADD_CARD_TO_HAND));

        assertThat(pileOf(result, RIVAL).hand())
                .extracting(CardInstance::definitionId)
                .containsExactly("fardeau");
        assertThat(pileOf(result, HERO).hand())
                .extracting(CardInstance::definitionId)
                .as("and not to the caster, who played it at somebody")
                .doesNotContain("fardeau");
    }

    @Test
    void addsToTheOpponentsDrawPile() {
        CombatResult result = play(giving(EffectType.ADD_CARD_TO_DRAW_PILE));

        assertThat(pileOf(result, RIVAL).draw())
                .extracting(CardInstance::definitionId)
                .contains("fardeau");
    }

    @Test
    void addsToTheOpponentsDiscardPile() {
        CombatResult result = play(giving(EffectType.ADD_CARD_TO_DISCARD_PILE));

        assertThat(pileOf(result, RIVAL).discard())
                .extracting(CardInstance::definitionId)
                .contains("fardeau");
    }

    /** With targetSelf set the caster keeps it, which is what a run has always done. */
    @Test
    void stillKeepsItWhenTheEffectPointsAtItsCaster() {
        CardDefinition card = new CardDefinition(
                "don", "Don", "", 0, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.ADD_CARD_TO_HAND, 1, true, null, 0, null, "fardeau")));

        CombatResult result = play(card);

        assertThat(pileOf(result, HERO).hand())
                .extracting(CardInstance::definitionId)
                .contains("fardeau");
        assertThat(pileOf(result, RIVAL).hand()).isEmpty();
    }

    private static CardPiles pileOf(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .piles();
    }

    private static CombatResult play(CardDefinition card) {
        CombatantState hero = duelist(HERO, "team-0", List.of(new CardInstance("played", card.id())));
        CombatantState rival = duelist(RIVAL, "team-1", List.of());
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, rival),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(RIVAL, 100, 1)),
                HERO, CombatCounters.initial());

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(card.id(), card, "fardeau", burden())),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played", List.of(RIVAL)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    /** Both sides of a duel are human, and both hold real piles. */
    private static CombatantState duelist(String id, String team, List<CardInstance> hand) {
        return new CombatantState(
                id, team, ControllerType.HUMAN, 80, 80, 0, 3, 3, 25,
                new CardPiles(List.of(), hand, List.of(), List.of()),
                List.of(), List.of(), null, 0);
    }
}

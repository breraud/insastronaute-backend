package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a turn does before the hand is dealt.
 *
 * <p>Unity opens a turn with {@code StartTurn} — armour, energy and every status — and only then
 * deals five cards ({@code TurnSystem.cs:164-187}). The engine dealt first and ticked afterwards,
 * so a status that draws, kills or heals acted on a hand that had already been filled. It also
 * topped the hand up to five instead of dealing five, which quietly cancelled the advantage of
 * keeping a card: retaining two cards should leave seven in hand, not five.
 */
class CombatEngineTurnStartOrderTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String HERO = "hero";
    private static final String OTHER = "other";

    @Test
    void dealsFiveCardsOnTopOfTheOnesTheHandKept() {
        CombatResult result = takeATurn(retainedHand());

        assertThat(hero(result).piles().hand()).hasSize(6);
    }

    @Test
    void letsTheStatusesActBeforeTheHandIsDealt() {
        CombatResult result = takeATurn(producer());

        List<CombatEvent> events = result.events();
        int statusDraw = indexOfFirst(events, event -> event instanceof CardMoved moved
                && "HAND".equals(moved.toPile()) && "NONE".equals(moved.fromPile()));
        int turnDraw = indexOfFirst(events, event -> event instanceof CardDrawn);

        assertThat(statusDraw).isNotNegative();
        assertThat(statusDraw).isLessThan(turnDraw);
    }

    private static int indexOfFirst(
            List<CombatEvent> events, java.util.function.Predicate<CombatEvent> match) {
        for (int index = 0; index < events.size(); index++) {
            if (match.test(events.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult takeATurn(CombatantState hero) {
        CardDefinition kept = new CardDefinition(
                "kept", 1, CardType.SKILL, fr.insastronaute.webapi.sts.combat.catalog.TargetingMode.SELF,
                Set.of(CardTag.RETAIN), List.of());
        CombatState state = state(hero);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(kept.id(), kept)), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), OTHER));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatantState retainedHand() {
        return hero(
                new CardPiles(
                        deck(6),
                        List.of(new CardInstance("kept-1", "kept")),
                        List.of(),
                        List.of()));
    }

    private static CombatantState producer() {
        return hero(
                new CardPiles(deck(8), List.of(), List.of(), List.of()),
                new StatusInstance("Production", 1, 3, null, 0));
    }

    private static List<CardInstance> deck(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> new CardInstance("card-" + index, "kept"))
                .map(CardInstance.class::cast)
                .toList();
    }

    private static CombatantState hero(CardPiles piles, StatusInstance... statuses) {
        return new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                piles,
                List.of(statuses),
                List.of(),
                null,
                0);
    }

    private static CombatState state(CombatantState hero) {
        CombatantState other = new CombatantState(
                OTHER,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                500,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, other),
                List.of(new TimelineEntry(OTHER, 0, 0), new TimelineEntry(HERO, 10, 1)),
                OTHER);
    }
}

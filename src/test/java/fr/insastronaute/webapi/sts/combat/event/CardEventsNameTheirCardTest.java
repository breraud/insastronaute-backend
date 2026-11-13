package fr.insastronaute.webapi.sts.combat.event;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An event that moves a card must say which card it is, not only which copy.
 *
 * <p>The client resolves a card by instance id and, when it has never seen that instance, builds
 * it from the definition the event names. With no definition named, it cannot: it drops the event
 * and the card never appears. That is every card the server creates mid-combat — added, drawn
 * after a transform, copied — and any card the client's own view has drifted away from.
 */
class CardEventsNameTheirCardTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000171");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void namesTheDefinitionOfEveryCardItDraws() {
        CombatResult result = endTurn();

        assertThat(result.events()).filteredOn(CardDrawn.class::isInstance)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(((CardDrawn) event).definitionId())
                        .isEqualTo("frappe"));
    }

    @Test
    void namesTheDefinitionOfEveryCardItMoves() {
        CombatResult result = endTurn();

        assertThat(result.events()).filteredOn(CardMoved.class::isInstance)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(((CardMoved) event).definitionId())
                        .isEqualTo("frappe"));
    }

    @Test
    void namesThePileItShuffled() {
        CombatResult result = endTurn();

        assertThat(result.events()).filteredOn(PileShuffled.class::isInstance)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(((PileShuffled) event).pile()).isEqualTo("DRAW"));
    }

    private static CombatResult endTurn() {
        CardDefinition strike =
                new CardDefinition("frappe", 1, TargetingMode.SINGLE_ENEMY, List.of());
        CombatState state = state();
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(strike.id(), strike)), new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), ENEMY));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state() {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(
                        List.of(new CardInstance("draw-1", "frappe")),
                        List.of(),
                        List.of(new CardInstance("discard-1", "frappe"),
                                new CardInstance("discard-2", "frappe")),
                        List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                200,
                new CardPiles(
                        List.of(),
                        List.of(new CardInstance("enemy-hand", "frappe")),
                        List.of(),
                        List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 0), new TimelineEntry(HERO, 50, 1)),
                ENEMY);
    }
}

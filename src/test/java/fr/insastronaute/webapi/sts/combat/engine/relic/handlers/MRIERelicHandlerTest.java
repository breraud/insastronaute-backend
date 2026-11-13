package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MRIERelicHandlerTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void onCombatStartAddsMeteoriteToDrawPileAndGalaxieToDiscardPile() {
        MRIERelicHandler handler = new MRIERelicHandler();

        CardInstance existingDraw = new CardInstance(UUID.randomUUID().toString(), "strike");
        CardInstance existingHand = new CardInstance(UUID.randomUUID().toString(), "defend");
        CombatantState owner = combatant(List.of(existingDraw), List.of(existingHand));
        CombatState state = state(owner);
        List<CombatEvent> events = new ArrayList<>();

        CombatantState result = handler.onCombatStart(state, owner, events);

        assertThat(result.piles().draw()).hasSize(2);
        assertThat(result.piles().draw().get(0)).isEqualTo(existingDraw);
        CardInstance meteoriteCard = result.piles().draw().get(1);
        assertThat(meteoriteCard.definitionId())
                .as("the catalog id, not the display name: the client compares this against the "
                        + "card's own id with an ordinal equality")
                .isEqualTo("meteorite");
        assertThat(meteoriteCard.instanceId()).isEqualTo("gen-MRIERelic-0-hero-0");

        assertThat(result.piles().hand()).containsExactly(existingHand);

        assertThat(result.piles().discard()).hasSize(1);
        CardInstance galaxieCard = result.piles().discard().get(0);
        assertThat(galaxieCard.definitionId()).isEqualTo("galaxie");
        assertThat(galaxieCard.instanceId()).isEqualTo("gen-MRIERelic-0-hero-1");

        assertThat(result.piles().exhaust()).isEmpty();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(CardMoved.class);
        CardMoved drawMoved = (CardMoved) events.get(0);
        assertThat(drawMoved.combatId()).isEqualTo(COMBAT_ID);
        assertThat(drawMoved.cardInstanceId()).isEqualTo(meteoriteCard.instanceId());
        assertThat(drawMoved.fromPile()).isEqualTo("NONE");
        assertThat(drawMoved.toPile()).isEqualTo("DRAW");
        assertThat(drawMoved.destinationIndex()).isEqualTo(1);

        assertThat(events.get(1)).isInstanceOf(CardMoved.class);
        CardMoved discardMoved = (CardMoved) events.get(1);
        assertThat(discardMoved.combatId()).isEqualTo(COMBAT_ID);
        assertThat(discardMoved.cardInstanceId()).isEqualTo(galaxieCard.instanceId());
        assertThat(discardMoved.fromPile()).isEqualTo("NONE");
        assertThat(discardMoved.toPile()).isEqualTo("DISCARD");
        assertThat(discardMoved.destinationIndex()).isEqualTo(0);
    }

    private static CombatantState combatant(List<CardInstance> draw, List<CardInstance> hand) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, 0, 3, 3, 100,
                new CardPiles(draw, hand, List.of(), List.of()),
                List.of(), List.of("MRIERelic"), null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                COMBAT_ID,
                0,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(123L),
                List.of(combatant, new CombatantState("enemy", "enemy-team", ControllerType.AI,
                        10, 10, 0, 3, 3, 100,
                        new CardPiles(List.of(), List.of(), List.of(), List.of()),
                        List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

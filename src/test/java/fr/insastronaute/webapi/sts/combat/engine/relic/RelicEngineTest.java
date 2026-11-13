package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelicEngineTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Test
    void processCombatStartDelegatesToHandlers() {
        RelicHandler mockHandler = mock(RelicHandler.class);
        when(mockHandler.id()).thenReturn("mock_relic");
        RelicEngine engine = new RelicEngine(List.of(mockHandler));

        CombatantState owner = combatant(List.of("mock_relic", "unknown_relic"));
        CombatState state = state(owner);

        CombatantState expectedResult = combatant(List.of());
        when(mockHandler.onCombatStart(any(), any(), any())).thenReturn(expectedResult);

        List<CombatEvent> events = new ArrayList<>();
        CombatantState result = engine.processCombatStart(owner, state, events);

        assertThat(result).isSameAs(expectedResult);
        verify(mockHandler).onCombatStart(state, owner, events);
    }

    @Test
    void processTurnStartDelegatesToHandlers() {
        RelicHandler mockHandler = mock(RelicHandler.class);
        when(mockHandler.id()).thenReturn("mock_relic");
        RelicEngine engine = new RelicEngine(List.of(mockHandler));

        CombatantState owner = combatant(List.of("mock_relic", "unknown_relic"));
        CombatState state = state(owner);

        CombatantState expectedResult = combatant(List.of());
        fr.insastronaute.webapi.sts.combat.model.RandomState random =
                new fr.insastronaute.webapi.sts.combat.model.RandomState(1L);
        fr.insastronaute.webapi.sts.combat.model.RandomState spent =
                new fr.insastronaute.webapi.sts.combat.model.RandomState(2L);
        // The four-argument form, which is the one the engine calls. A mock does not run the
        // interface's default body, so stubbing the three-argument one leaves this returning null.
        when(mockHandler.onTurnStart(any(), any(), any(), any()))
                .thenReturn(new RelicHandler.TurnStart(expectedResult, spent));

        List<CombatEvent> events = new ArrayList<>();
        RelicHandler.TurnStart step = engine.processTurnStart(owner, state, random, events);

        assertThat(step.combatant()).isSameAs(expectedResult);
        assertThat(step.random())
                .as("what a handler spends is carried on to the next and back to the caller")
                .isSameAs(spent);
        verify(mockHandler).onTurnStart(state, owner, random, events);
    }

    private static CombatantState combatant(List<String> relics) {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 25, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), relics, null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(123L),
                List.of(
                        combatant,
                        new CombatantState("enemy", "enemy-team", ControllerType.AI,
                                10, 10, 0, 3, 3, 100,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, 0)),
                List.of(
                        new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.StatusEngine;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SafetyNetRelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Statuses the engine writes for its own bookkeeping are not part of the game.
 *
 * <p>A relic that may only fire once per combat remembers it by putting a marker on its owner.
 * The catalog does not declare those markers, so they counted as neutral statuses: a card that
 * dispels debuffs stripped them and handed the relic a second use, and the ones written with a
 * duration of zero deleted themselves at the very next turn. Neither is a game rule; both are
 * the engine tripping over its own notes.
 */
class EngineMarkerStatusTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    @Test
    void refusesToLetACardStripAMarkerTheCatalogNeverDeclared() {
        StatusInstance marker = new StatusInstance("SafetyNetUsed", 1, -1, "", 0);

        assertThat(StatusCatalog.isDispellable(marker, false)).isFalse();
        assertThat(StatusCatalog.isDispellable(marker, true)).isFalse();
    }

    @Test
    void stillLetsACardStripADeclaredDebuff() {
        StatusInstance vulnerable = new StatusInstance("Vuln", 0, 2, "", 0);

        assertThat(StatusCatalog.isDispellable(vulnerable, false)).isTrue();
    }

    @Test
    void keepsAOncePerCombatMarkerAcrossATurnBoundary() {
        SafetyNetRelicHandler relic = new SafetyNetRelicHandler();
        CombatantState wounded = wounded();

        CombatantState afterFirstSave = owner(relic.onDamageTaken(
                wounded, 5, List.of(wounded), List.of(), new RandomState(1), COMBAT_ID, 1L,
                new java.util.ArrayList<>()));
        CombatantState afterATurn = StatusEngine
                .processTurnStart(afterFirstSave, 2L, COMBAT_ID, new RandomState(1))
                .combatant();
        CombatantState afterSecondBlow = owner(relic.onDamageTaken(
                afterATurn, 5, List.of(afterATurn), List.of(), new RandomState(1), COMBAT_ID, 3L,
                new java.util.ArrayList<>()));

        assertThat(afterFirstSave.hp()).isEqualTo(14);
        assertThat(afterSecondBlow.hp()).isEqualTo(afterATurn.hp());
    }

    private static CombatantState owner(ResolutionStep step) {
        return step.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    private static CombatantState wounded() {
        return new CombatantState(
                "hero",
                "players",
                ControllerType.HUMAN,
                30,
                10,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(),
                List.of("SafetyNetRelic"),
                null,
                0);
    }
}

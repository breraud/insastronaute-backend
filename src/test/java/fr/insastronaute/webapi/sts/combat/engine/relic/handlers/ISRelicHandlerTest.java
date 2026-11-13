package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Annales des IS : « Au premier tour d'un combat, gagnez 10 d'Armure. »
 *
 * <p>Ce qui est eprouve ici est le mot « premier ». La relique donnait l'armure a tous les tours,
 * et l'ancien test - un seul appel, une seule assertion - passait pareil dans les deux cas.</p>
 */
class ISRelicHandlerTest {

    private final ISRelicHandler handler = new ISRelicHandler();

    @Test
    void theFirstTurnGrantsTheArmour() {
        CombatantState owner = combatant();

        CombatantState afterFirst = handler.onTurnStart(state(owner), owner, new ArrayList<>());

        assertThat(afterFirst.armor()).isEqualTo(10);
    }

    @Test
    void theTurnsAfterTheFirstGrantNothing() {
        CombatantState owner = combatant();

        CombatantState afterFirst = handler.onTurnStart(state(owner), owner, new ArrayList<>());
        CombatantState afterSecond = handler.onTurnStart(state(afterFirst), afterFirst, new ArrayList<>());
        CombatantState afterThird = handler.onTurnStart(state(afterSecond), afterSecond, new ArrayList<>());

        assertThat(afterSecond.armor()).isEqualTo(10);
        assertThat(afterThird.armor()).isEqualTo(10);
    }

    /** La memoire vit dans l'etat du combattant, donc un nouveau combat repart a zero. */
    @Test
    void aFreshCombatGrantsItAgain() {
        CombatantState spent = handler.onTurnStart(state(combatant()), combatant(), new ArrayList<>());
        CombatantState nextCombat = combatant();

        CombatantState granted = handler.onTurnStart(state(nextCombat), nextCombat, new ArrayList<>());

        assertThat(spent.armor()).isEqualTo(10);
        assertThat(granted.armor()).isEqualTo(10);
    }

    private static CombatantState combatant() {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("ISRelic"), null, 0);
    }

    private static CombatState state(CombatantState combatant) {
        return new CombatState(
                UUID.randomUUID(),
                12, "v1", CombatStatus.ACTIVE, null,
                new RandomState(123L),
                List.of(combatant, new CombatantState("enemy", "enemy-team", ControllerType.AI,
                        10, 10, 0, 3, 3, 100,
                        new CardPiles(List.of(), List.of(), List.of(), List.of()),
                        List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}

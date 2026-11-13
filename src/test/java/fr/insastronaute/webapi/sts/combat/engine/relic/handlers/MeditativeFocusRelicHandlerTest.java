package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
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
 * Eco-cup : « La premiere fois que vous etes soigne a chaque combat, gagnez 2 d'energie. »
 *
 * <p>« La premiere fois » est tout le sujet : la relique donnait les 2 d'energie a chaque soin.</p>
 */
class MeditativeFocusRelicHandlerTest {

    private final MeditativeFocusRelicHandler handler = new MeditativeFocusRelicHandler();

    @Test
    void theFirstHealGrantsTheEnergy() {
        CombatantState owner = combatant();

        RelicHandler.HealInterceptionResult healed =
                handler.onHeal(state(owner), owner, 5, new ArrayList<>());

        assertThat(healed.owner().energy()).isEqualTo(5);
        assertThat(healed.remainingHeal()).isEqualTo(5);
    }

    @Test
    void theHealsAfterTheFirstGrantNothing() {
        CombatantState owner = combatant();

        CombatantState afterFirst = handler.onHeal(state(owner), owner, 5, new ArrayList<>()).owner();
        RelicHandler.HealInterceptionResult second =
                handler.onHeal(state(afterFirst), afterFirst, 5, new ArrayList<>());

        assertThat(second.owner().energy()).isEqualTo(5);
        assertThat(second.remainingHeal()).isEqualTo(5);
    }

    /** Un soin de zero n'est pas un soin : il ne doit pas consommer la fois unique. */
    @Test
    void aHealOfNothingDoesNotSpendTheOnce() {
        CombatantState owner = combatant();

        CombatantState afterNothing = handler.onHeal(state(owner), owner, 0, new ArrayList<>()).owner();
        RelicHandler.HealInterceptionResult real =
                handler.onHeal(state(afterNothing), afterNothing, 5, new ArrayList<>());

        assertThat(afterNothing.energy()).isEqualTo(3);
        assertThat(real.owner().energy()).isEqualTo(5);
    }

    private static CombatantState combatant() {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("MeditativeFocusRelic"), null, 0);
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

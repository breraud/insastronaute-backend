package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import java.util.List;
import java.util.ArrayList;

class CrownOfAshesRelicTest {

    @Test
    void testId() {
        CrownOfAshesRelic handler = new CrownOfAshesRelic();
        assertThat(handler.id()).isEqualTo("CrownOfAshesRelic");
    }

    @Test
    void grantsEnergyOnTurnStartWhileUnderThreeTurns() {
        CrownOfAshesRelic handler = new CrownOfAshesRelic();
        CombatantState owner = new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 25, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("CrownOfAshesRelic"), null, 0);

        CombatantState result = handler.onTurnStart(null, owner, new ArrayList<>());

        assertThat(result.energy()).isEqualTo(4);
    }

    @Test
    void staysQuietOnceThreeTurnsHaveAlreadyBeenGranted() {
        CrownOfAshesRelic handler = new CrownOfAshesRelic();
        CombatantState owner = new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 25, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("CrownOfAshesRelic"), null, 0)
                .remembering("CrownOfAshesRelic", "turnsGranted", 3);

        CombatantState result = handler.onTurnStart(null, owner, new ArrayList<>());

        assertThat(result).isSameAs(owner);
    }
}

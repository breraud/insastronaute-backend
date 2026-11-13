package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class FireRelicTest {
    @Test
    void testLogic() {
        FireRelic handler = new FireRelic();
        CombatantState owner = new CombatantState("hero", "hero-team", ControllerType.HUMAN, 50, 50, 0, 3, 3, 100, new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatantState result = handler.onCombatStart(null, owner, new ArrayList<>());
        assertThat(result.armor()).isEqualTo(10);
    }
}

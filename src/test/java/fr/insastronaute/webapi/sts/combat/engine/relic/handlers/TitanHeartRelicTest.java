package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import java.util.List;
import java.util.ArrayList;

class TitanHeartRelicTest {

    @Test
    void testId() {
        TitanHeartRelic handler = new TitanHeartRelic();
        assertThat(handler.id()).isEqualTo("TitanHeartRelic");
    }

    @Test
    void testDefaultLogic() {
        TitanHeartRelic handler = new TitanHeartRelic();
        CombatantState owner = new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 25, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("TitanHeartRelic"), null, 0);
        
        CombatantState result = handler.onTurnStart(null, owner, new ArrayList<>());
        assertThat(result).isSameAs(owner);
    }
}

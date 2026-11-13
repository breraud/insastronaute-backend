package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;

class ArtifactCharmRelicHandlerTest {

    @Test
    void onCombatStart_addsArtifactStatus() {
        ArtifactCharmRelicHandler handler = new ArtifactCharmRelicHandler();
        CombatantState owner = combatant();
        CombatState state = state(owner);

        CombatantState result = handler.onCombatStart(state, owner, new ArrayList<>());

        assertThat(result.statuses()).anyMatch(s -> s.statusType().equals("Artifact") && s.value() == 1);
    }

    private static CombatantState combatant() {
        return new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN,
                50, 50, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("ArtifactCharmRelic"), null, 0);
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

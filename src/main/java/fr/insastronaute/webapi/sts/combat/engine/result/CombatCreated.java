package fr.insastronaute.webapi.sts.combat.engine.result;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import java.util.List;
import java.util.Objects;

public record CombatCreated(CombatState state, List<CombatEvent> events) {

    public CombatCreated {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(events);
    }
}

package fr.insastronaute.webapi.sts.combat.engine.result;

import fr.insastronaute.webapi.sts.combat.command.CommandRejection;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import java.util.List;
import java.util.Objects;

public record CombatResult(
        CombatState state,
        List<CombatEvent> events,
        CommandRejection rejection
) {

    public CombatResult {
        Objects.requireNonNull(state, "state must not be null");
        events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        if (rejection != null && !events.isEmpty()) {
            throw new IllegalArgumentException("rejected results must not contain events");
        }
    }

    public boolean accepted() {
        return rejection == null;
    }

    public static CombatResult accepted(CombatState state, List<CombatEvent> events) {
        return new CombatResult(state, events, null);
    }

    public static CombatResult rejected(
            CombatState state,
            RejectionCode code,
            String message
    ) {
        return new CombatResult(state, List.of(), new CommandRejection(code, message));
    }
}

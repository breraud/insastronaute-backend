package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

/**
 * Depression: its owner takes no status at all, helpful or otherwise
 * ({@code StatusNullificationRelic.cs}).
 */
@Component
public class StatusNullificationRelic implements RelicHandler {

    @Override
    public String id() {
        return "StatusNullificationRelic";
    }

    @Override
    public StatusGate canApplyStatus(CombatantState owner, StatusInstance incoming) {
        return new StatusGate(false, owner);
    }
}

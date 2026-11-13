package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

/**
 * Temporal anchor: its owner's turn cannot be moved in the order, forwards, back or by cutting
 * in ({@code TemporalAnchorRelic.cs}).
 */
@Component
public class TemporalAnchorRelic implements RelicHandler {

    @Override
    public String id() {
        return "TemporalAnchorRelic";
    }

    @Override
    public boolean canTurnBeMoved(CombatantState owner, boolean advancing, boolean cuttingIn) {
        return false;
    }
}

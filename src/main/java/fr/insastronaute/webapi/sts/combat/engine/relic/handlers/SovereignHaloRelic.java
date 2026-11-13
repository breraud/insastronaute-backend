package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code Boss/SovereignHaloRelic.cs}: at combat start, +3 Artefact.
 *
 * <p>The relic also costs 5 max HP on acquisition ({@code OnAcquire}/{@code LoseMaxHP}); that is
 * a run-level effect outside a combat's scope and is not implemented here.
 */
@Component
public class SovereignHaloRelic implements RelicHandler {

    @Override
    public String id() {
        return "SovereignHaloRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Artifact", 3, -1, "", 0));
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}

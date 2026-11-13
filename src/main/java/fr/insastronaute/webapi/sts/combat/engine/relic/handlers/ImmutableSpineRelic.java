package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

/**
 * Apéro: the first debuff its owner would take each combat is refused, and heals them two
 * ({@code ImmutableSpineRelic.cs}).
 */
@Component
public class ImmutableSpineRelic implements RelicHandler {

    private static final String USED = "used";

    @Override
    public String id() {
        return "ImmutableSpineRelic";
    }

    @Override
    public StatusGate canApplyStatus(CombatantState owner, StatusInstance incoming) {
        if (owner.recall(id(), USED) != 0 || !StatusCatalog.isDebuff(incoming)) {
            return new StatusGate(true, owner);
        }
        CombatantState healed = new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                Math.min(owner.maxHp(), owner.hp() + 2),
                owner.armor(),
                owner.energy(),
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(),
                owner.relicMemory()).remembering(id(), USED, 1);
        return new StatusGate(false, healed);
    }
}

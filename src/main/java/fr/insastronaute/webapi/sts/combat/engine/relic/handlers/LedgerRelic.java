package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.DrawEventFactory;
import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Mirrors {@code Common/LedgerRelic.cs}: draw 1 card at the start of the owner's first turn.
 *
 * <p>Unity grants it on {@code OnTurnStart} guarded by a one-shot flag; the Java engine never
 * calls {@code onTurnStart} for the very first turn of a combat (see {@code CombatFactory}), so
 * granting it directly in {@code onCombatStart} — which fires exactly once, right after the
 * opening hand is dealt — reproduces the same one-time draw for the combatant who acts first.
 */
@Component
public class LedgerRelic implements RelicHandler {

    @Override
    public String id() {
        return "LedgerRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        PileOperations.DrawResult draw = PileOperations.draw(owner.piles(), 1, state.random());
        events.addAll(DrawEventFactory.create(
                state.combatId(), state.revision(), owner.combatantId(),
                owner.piles().hand().size(), draw));

        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), draw.piles(),
                owner.statuses(), owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}

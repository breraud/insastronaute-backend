package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.DrawEventFactory;
import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code Boss/InfernalContractRelic.cs}: at combat start, +2 energy, draw 2 cards, and
 * Poison.
 *
 * <p>Unity applies {@code StatusEffect.Factory(StatusType.Poison, 6, 6)}: {@code PoisonStatus}
 * only reads the duration argument, so this grants 6 stacks of Poison, not the 4 the relic's
 * {@code description} advertises. Followed as written in the C#, per the task's instruction to
 * defer to the code over the description.
 *
 * <p>Unity gates the energy/draw on {@code OnTurnStart} (guarded by a one-shot flag) purely so the
 * grant survives its own start-of-turn reset; the Java engine runs no such reset before the very
 * first turn (see {@code CombatFactory}), so granting them directly here reproduces the same
 * one-time effect for the combatant who acts first.
 */
@Component
public class InfernalContractRelic implements RelicHandler {

    @Override
    public String id() {
        return "InfernalContractRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Poison", 0, 6, "", 0));

        int newEnergy = owner.energy() + 2;

        PileOperations.DrawResult draw = PileOperations.draw(owner.piles(), 2, state.random());
        events.addAll(DrawEventFactory.create(
                state.combatId(), state.revision(), owner.combatantId(),
                owner.piles().hand().size(), draw));

        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), newEnergy,
                owner.baseEnergy(), owner.turnDelayTicks(), draw.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}

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
 * Mirrors {@code Boss/SolarCageRelic.cs}: at combat start, +1 energy and draw 3 cards; at the end
 * of the owner's first turn, 10 true damage.
 *
 * <p>Unity grants the energy/draw through {@code OnTurnStart} guarded by a one-shot flag, purely
 * so it survives its own start-of-turn energy reset; the Java engine runs no such reset before
 * the very first turn, so granting it directly in {@code onCombatStart} reproduces the same
 * one-time effect for the combatant who acts first.
 *
 * <p>The end-of-turn damage bypasses armour ({@code TakeDamage(10, ignoreArmor: true)}), and is
 * applied as a direct hp reduction: {@code onTurnEnd} only has the owner's own state to rebuild,
 * not the combatants list a full damage-taken resolution needs, so this does not run through the
 * relic/event damage-taken pipeline the way a card's damage would.
 */
@Component
public class SolarCageRelic implements RelicHandler {

    private static final String TRIGGERED = "triggered";

    @Override
    public String id() {
        return "SolarCageRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        int newEnergy = owner.energy() + 1;

        PileOperations.DrawResult draw = PileOperations.draw(owner.piles(), 3, state.random());
        events.addAll(DrawEventFactory.create(
                state.combatId(), state.revision(), owner.combatantId(),
                owner.piles().hand().size(), draw));

        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), newEnergy,
                owner.baseEnergy(), owner.turnDelayTicks(), draw.piles(),
                owner.statuses(), owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }

    @Override
    public CombatantState onTurnEnd(CombatState state, CombatantState owner, List<CombatEvent> events) {
        if (owner.recall(id(), TRIGGERED) != 0) {
            return owner;
        }

        int newHp = Math.max(0, owner.hp() - 10);
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), newHp, owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                owner.statuses(), owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory())
                .remembering(id(), TRIGGERED, 1);
    }
}

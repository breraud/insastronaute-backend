package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scales with how long the target still has to wait for its turn.
 *
 * <p>A target that is about to act contributes nothing, which is what makes these cards reward
 * hitting something whose turn is far away.
 */
public record TimeUntilNextTurnModifier(StatType type, int perTick) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        if (ctx == null || ctx.target() == null || ctx.timeline() == null || ctx.timeline().isEmpty()) {
            return value;
        }
        List<TimelineEntry> timeline = ctx.timeline();
        TimelineEntry entry = timeline.stream()
                .filter(candidate -> candidate.combatantId().equals(ctx.target().combatantId()))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            return value;
        }
        // The wait is measured against a turn that will actually happen. A corpse keeps its
        // place on the timeline — a legal state since turns stopped going to the dead — and
        // it sits at a tick no living entry can undercut, so counting from it stretched the
        // wait and made every such card hit for MORE than it should. Measuring from the first
        // living entry is therefore a nerf to every card scaled this way, not a buff.
        Set<String> living = new HashSet<>();
        for (CombatantState combatant : ctx.combatants()) {
            if (combatant.hp() > 0) {
                living.add(combatant.combatantId());
            }
        }
        TimelineEntry firstLiving = timeline.stream()
                .filter(candidate -> living.contains(candidate.combatantId()))
                .findFirst()
                .orElse(null);
        if (firstLiving == null) {
            return value;
        }
        long wait = entry.readyAtTick() - firstLiving.readyAtTick();
        return value + (int) (perTick * wait);
    }
}

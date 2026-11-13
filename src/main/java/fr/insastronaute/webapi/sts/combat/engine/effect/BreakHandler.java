package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.ArmorBroken;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Destroys the whole of a target's armour at once.
 *
 * <p>The relics are asked about it here, exactly as {@code DamageHandler} asks them when a blow
 * takes the last of somebody's armour. They used not to be: this handler announced the
 * {@link ArmorBroken} event and stopped there, so a relic watching for broken armour answered a
 * sword and never answered the effect named after it -- the Grand Magistrat's, which delays the
 * turn of whoever it breaks, went silent on precisely the cards written to trigger it.
 */
public final class BreakHandler implements EffectHandler {

    private final RelicEngine relicEngine;

    public BreakHandler(RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    /** Without a relic engine to ask: the armour still falls, nobody is told. */
    public BreakHandler() {
        this(null);
    }

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        // Ancrage tient l'armure contre ce qui la détruirait d'un coup. Il n'empêche pas de la
        // percer petit à petit : seule la destruction instantanée le regarde, c'est-à-dire cet
        // effet-ci.
        if (target.armor() <= 0 || guardsArmor(target)) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        int armorLost = target.armor();
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                0, // Set armor to 0
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        List<CombatEvent> events = new ArrayList<>();
        events.add(new ArmorBroken(
                combatId,
                revision,
                sourceId,
                target.combatantId(),
                armorLost
        ));

        if (relicEngine == null) {
            return new ResolutionStep(updated, timeline, events, random);
        }

        // The same two questions DamageHandler asks of a break: what the one who lost the armour
        // does about it, and what the one who broke it gains from it. The first reads the struck
        // one's relics, the second the striker's, and neither carries the other's.
        CombatantState source = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);

        List<TimelineEntry> currentTimeline =
                relicEngine.processArmorBroken(source, updated, timeline, events);
        CombatantState strikerAfterBreak = relicEngine.processTargetArmorBroken(source, updated);

        List<CombatantState> updatedCombatants = new ArrayList<>(combatants);
        replace(updatedCombatants, updated);
        if (strikerAfterBreak != null && source != null && !strikerAfterBreak.equals(source)) {
            replace(updatedCombatants, strikerAfterBreak);
        }

        return new ResolutionStep(updatedCombatants, currentTimeline, events, random);
    }

    private static void replace(List<CombatantState> combatants, CombatantState combatant) {
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(combatant.combatantId())) {
                combatants.set(index, combatant);
                return;
            }
        }
    }

    /** @return true when a status on the target refuses the instant destruction of its armour */
    private static boolean guardsArmor(CombatantState target) {
        return target.statuses().stream()
                .anyMatch(status -> "Steadfast".equals(status.statusType()));
    }
}

package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import java.util.ArrayList;

public final class ArmorHandler implements EffectHandler {

    private final RelicEngine relicEngine;

    public ArmorHandler(RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    public ArmorHandler() {
        this.relicEngine = null;
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
        CombatantState source = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(target);
        EffectContext ctx = new EffectContext(
                null, source, target, List.of(target), card, false, cardModifiers, timeline, combatants);
        // Armour used to be applied raw, so Dexterity, the Protection enchantment and every
        // armour modifier changed nothing.
        int armor = Math.max(0, BattleCalculator.getModifiedValue(
                effect.value(), StatType.ARMOR, ctx));
        long capacity = (long) Integer.MAX_VALUE - target.armor();
        int appliedArmor = (int) Math.min((long) armor, capacity);
        int resultingArmor = target.armor() + appliedArmor;
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                resultingArmor,
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        ArmorGained event = new ArmorGained(
                combatId,
                revision,
                sourceId,
                target.combatantId(),
                armor,
                appliedArmor,
                resultingArmor
        );

        List<CombatEvent> events = new ArrayList<>();
        events.add(event);

        List<CombatantState> currentCombatants = new ArrayList<>(combatants);
        for (int i = 0; i < currentCombatants.size(); i++) {
            if (currentCombatants.get(i).combatantId().equals(updated.combatantId())) {
                currentCombatants.set(i, updated);
            }
        }

        if (relicEngine != null && appliedArmor > 0) {
            currentCombatants = relicEngine.processArmorGain(updated, appliedArmor, currentCombatants, events);
            // Re-fetch updated target just in case
            final String updatedId = updated.combatantId();
            final CombatantState defaultUpdated = updated;
            updated = currentCombatants.stream().filter(c -> c.combatantId().equals(updatedId)).findFirst().orElse(defaultUpdated);
        }

        return new ResolutionStep(currentCombatants, timeline, events, random);
    }
}
